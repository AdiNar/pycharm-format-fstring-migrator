/*
   Copyright 2018 Adrian Naruszko

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package main.adinar;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;

public class FstringMigratorAction extends AnAction {
    @Override
    public void actionPerformed(final AnActionEvent e) {
        final Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
        final Project project = e.getProject();

        final Document document = editor.getDocument();
        final SelectionModel selectionModel = editor.getSelectionModel();

        final int start = selectionModel.getSelectionStart();
        final int end = selectionModel.getSelectionEnd();

        WriteCommandAction.runWriteCommandAction(project, () -> {
                    String selection = document.getText(new TextRange(start, end));

                    document.replaceString(start, end, new FstringMigrator(selection).getResultReplace());
                    selectionModel.removeSelection();
                }
        );
        selectionModel.removeSelection();
    }
}
