package edu.isi.karma.controller.command.worksheet;

import javax.servlet.http.HttpServletRequest;

import org.json.JSONArray;
import org.json.JSONException;

import edu.isi.karma.controller.command.Command;
import edu.isi.karma.controller.command.JSONInputCommandFactory;
import edu.isi.karma.rep.Worksheet;
import edu.isi.karma.rep.Workspace;
import edu.isi.karma.util.CommandInputJSONUtil;
import edu.isi.karma.webserver.KarmaException;

public class FoldCommandFactory extends JSONInputCommandFactory {

	public enum Arguments {
		worksheetId, hTableId, hNodeId, 
		newColumnName, defaultValue, selectionName
	}
	
	@Override
	public Command createCommand(HttpServletRequest request,
			Workspace workspace) {
		String hNodeId = request.getParameter(Arguments.hNodeId.name());
		String worksheetId = request.getParameter(Arguments.worksheetId.name());
		String selectionName = request.getParameter(Arguments.selectionName.name());
		Worksheet ws = workspace.getWorksheet(worksheetId);
		return new FoldCommand(getNewId(workspace), worksheetId, 
				"", hNodeId, 
				ws.getSuperSelectionManager().getSuperSelection(selectionName));
	}

	@Override
	public Command createCommand(JSONArray inputJson, Workspace workspace)
			throws JSONException, KarmaException {
		/** Parse the input arguments and create proper data structures to be passed to the command **/
		String hNodeID = CommandInputJSONUtil.getStringValue(Arguments.hNodeId.name(), inputJson);
		String worksheetId = CommandInputJSONUtil.getStringValue(Arguments.worksheetId.name(), inputJson);
		String selectionName = CommandInputJSONUtil.getStringValue(Arguments.selectionName.name(), inputJson);
		Worksheet ws = workspace.getWorksheet(worksheetId);
		FoldCommand foldCmd = new FoldCommand(getNewId(workspace), worksheetId,
				"", hNodeID, 
				ws.getSuperSelectionManager().getSuperSelection(selectionName));
		foldCmd.setInputParameterJson(inputJson.toString());
		return foldCmd;
	}

	@Override
	public Class<? extends Command> getCorrespondingCommand() {
	
		return FoldCommand.class;
	}

}
