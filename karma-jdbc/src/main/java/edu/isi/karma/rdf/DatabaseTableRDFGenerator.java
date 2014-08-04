/*******************************************************************************
 * Copyright 2012 University of Southern California
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * 	http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * This code was developed by the Information Integration Group as part 
 * of the Karma project at the Information Sciences Institute of the 
 * University of Southern California.  For more information, publications, 
 * and related projects, please see: http://www.isi.edu/integration
 ******************************************************************************/

package edu.isi.karma.rdf;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.isi.karma.controller.command.selection.SuperSelection;
import edu.isi.karma.kr2rml.ErrorReport;
import edu.isi.karma.kr2rml.KR2RMLRDFWriter;
import edu.isi.karma.kr2rml.KR2RMLWorksheetRDFGenerator;
import edu.isi.karma.kr2rml.mapping.KR2RMLMapping;
import edu.isi.karma.kr2rml.mapping.R2RMLMappingIdentifier;
import edu.isi.karma.kr2rml.mapping.WorksheetR2RMLJenaModelParser;
import edu.isi.karma.rep.HNode;
import edu.isi.karma.rep.HTable;
import edu.isi.karma.rep.RepFactory;
import edu.isi.karma.rep.Row;
import edu.isi.karma.rep.Table;
import edu.isi.karma.rep.Worksheet;
import edu.isi.karma.rep.Workspace;
import edu.isi.karma.rep.HNode.HNodeType;
import edu.isi.karma.util.AbstractJDBCUtil;
import edu.isi.karma.util.DBType;
import edu.isi.karma.util.JDBCUtilFactory;
import edu.isi.karma.webserver.KarmaException;


public class DatabaseTableRDFGenerator extends RdfGenerator {
	
	private static Logger logger = LoggerFactory.getLogger(DatabaseTableRDFGenerator.class);
	private DBType dbType;
	private String hostname;
	private int portnumber;
	private String username;
	private String password;
	private String dBorSIDName;
	private String encoding;
	private static int DATABASE_TABLE_FETCH_SIZE = 10000;
	
	public DatabaseTableRDFGenerator(DBType dbType, String hostname,
			int portnumber, String username, String password,
			String dBorSIDName, String encoding, SuperSelection sel) {
		super(sel);
		this.dbType = dbType;
		this.hostname = hostname;
		this.portnumber = portnumber;
		this.username = username;
		this.password = password;
		this.dBorSIDName = dBorSIDName;
		this.encoding = encoding;
	}
	
	public void generateRDFFromSQL(String query, List<KR2RMLRDFWriter> writers, R2RMLMappingIdentifier id, String baseURI)
			throws IOException, JSONException, KarmaException, SQLException, ClassNotFoundException {
		String wkname = query.replace(" ", "_");
		if(wkname.length() > 100)
			wkname = wkname.substring(0, 99) + "...";
		generateRDF(wkname, query, writers, id, baseURI);
	}
	
	public void generateRDFFromTable(String tablename, List<KR2RMLRDFWriter> writers, R2RMLMappingIdentifier id, String baseURI)
			throws IOException, JSONException, KarmaException, SQLException, ClassNotFoundException {
		AbstractJDBCUtil dbUtil = JDBCUtilFactory.getInstance(dbType);
		String query = "Select * FROM " + dbUtil.escapeTablename(tablename);
		generateRDF(tablename, query, writers, id, baseURI);
	}

	private void generateRDF(String wkname, String query, List<KR2RMLRDFWriter> writers, R2RMLMappingIdentifier id, String baseURI) 
			throws IOException, JSONException, KarmaException, SQLException, ClassNotFoundException{
		logger.debug("Generating RDF...");

		WorksheetR2RMLJenaModelParser parserTest = new WorksheetR2RMLJenaModelParser(id);
		KR2RMLMapping mapping = parserTest.parse();
		
		AbstractJDBCUtil dbUtil = JDBCUtilFactory.getInstance(dbType);
		Connection conn = dbUtil.getConnection(hostname, portnumber, username, password, dBorSIDName);
		conn.setAutoCommit(false);
		
		java.sql.Statement stmt = conn.createStatement(java.sql.ResultSet.TYPE_FORWARD_ONLY,
				java.sql.ResultSet.CONCUR_READ_ONLY);
		stmt.setFetchSize(DATABASE_TABLE_FETCH_SIZE);
		
		ResultSet r = stmt.executeQuery(query);
		ResultSetMetaData meta = r.getMetaData();;
		
		// Get the column names
		List<String> columnNames = new ArrayList<>();
		for (int i = 1; i <= meta.getColumnCount(); i++) {
			columnNames.add(meta.getColumnName(i));
		}
		
		// Prepare required Karma objects
	     Workspace workspace = initializeWorkspace();
 	
		RepFactory factory = workspace.getFactory();
		Worksheet wk = factory.createWorksheet(wkname, workspace, encoding);
		List<String> headersList = addHeaders(wk, columnNames, factory);
		
		int counter = 0;
		
		ArrayList<String> rowValues = null;
		while ((rowValues = dbUtil.parseResultSetRow(r)) != null) {
			// Generate RDF and create a new worksheet for every DATABASE_TABLE_FETCH_SIZE rows
			if(counter%DATABASE_TABLE_FETCH_SIZE == 0 && counter != 0) {
				generateRDFFromWorksheet(wk, workspace, mapping, writers, baseURI);
				logger.debug("Done for " + counter + " rows ..." );
			    removeWorkspace(workspace);
			    
			    parserTest = new WorksheetR2RMLJenaModelParser(id);
				mapping = parserTest.parse();
			    workspace = initializeWorkspace();
			    factory = workspace.getFactory();
				wk = factory.createWorksheet(wkname, workspace, encoding);
				headersList = addHeaders(wk, columnNames, factory);
				
			}
			
			/** Add the data **/
	        Table dataTable = wk.getDataTable();
	        Row row = dataTable.addRow(factory);
	        for(int i=0; i<rowValues.size(); i++) {
	        	row.setValue(headersList.get(i), rowValues.get(i), factory);
	        }
			
			counter++;
		}
		
		generateRDFFromWorksheet(wk, workspace, mapping, writers, baseURI);
		
		// Releasing all the resources
		r.close();
		conn.close();
		stmt.close();
		logger.debug("done");
	}
	
	private void generateRDFFromWorksheet(Worksheet wk, 
			Workspace workspace, KR2RMLMapping mapping, List<KR2RMLRDFWriter> writers, String baseURI) 
					throws IOException, JSONException, KarmaException {
		// Generate RDF for the remaining rows
		// Gets all the errors generated during the RDF generation
		ErrorReport errorReport = new ErrorReport();
		
		this.applyHistoryToWorksheet(workspace, wk, mapping);
		
		// RDF generation object initialization
		KR2RMLWorksheetRDFGenerator rdfGen = new KR2RMLWorksheetRDFGenerator(wk,
				workspace.getFactory(), workspace.getOntologyManager(), writers, false,
				mapping, errorReport, selection);

		// Generate the rdf
		rdfGen.generateRDF(false);
	}
	
	private List<String> addHeaders (Worksheet wk, List<String> columnNames,
			RepFactory factory) {
		HTable headers = wk.getHeaders();
		ArrayList<String> headersList = new ArrayList<String>();
        for(int i=0; i< columnNames.size(); i++){
        	HNode hNode = null;
        	hNode = headers.addHNode(columnNames.get(i), HNodeType.Regular, wk, factory);
        	headersList.add(hNode.getId());
        }
        return headersList;
	}
}
