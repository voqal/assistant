const openFilesInfo = await Promise.all(
    vscode.window.tabGroups.all
        .flatMap(group => group.tabs)
        .filter(tab => tab.input instanceof vscode.TabInputText)
        .map(async tab => {
            const doc = await vscode.workspace.openTextDocument(tab.input.uri);
            const fsPath = doc.uri.fsPath.replace(/\\/g, '/');
            return {
                path: fsPath,
                filename: fsPath.split('/').pop(),
                language: doc.languageId,
                code: doc.getText(),
                codeWithLineNumbers: doc.getText().split('\n').map((line, index) => `${index + 1}|${line}`).join('\n')
            };
        })
);

const response = {
    status: 'success',
    result: openFilesInfo
};
return response;