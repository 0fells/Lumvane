#include "include/ui/widget/JsonEditor.hpp"

#include "include/global/Configs.hpp"
#include "include/ui/setting/ThemeManager.hpp"

#include <QJsonDocument>

namespace {
    QString jsonError(const QString &source) {
        QJsonParseError error{};
        QJsonDocument::fromJson(source.toUtf8(), &error);
        return error.error == QJsonParseError::NoError ? QString() : error.errorString();
    }

    QString jsonToString(const QJsonObject &object) {
        return QJsonObject2QString(object, false);
    }

    QJsonObject jsonFromString(const QString &text) {
        return QString2QJsonObject(text);
    }

    void markInvalid(QWidget *w, bool invalid) {
        if (!invalid) {
            w->setPalette(QPalette());
            return;
        }
        auto palette = w->palette();
        palette.setColor(QPalette::Text, ThemeManager::Tokens().bad);
        w->setPalette(palette);
    }
}

JsonEditor::JsonEditor(const QJsonObject& rootObject, QWidget* parent) : QDialog(parent) {
    setupUi(this);
    original = rootObject;
    final = rootObject;
    QString jsonString = jsonToString(rootObject);

    if (jsonError(jsonString).isEmpty()) {
        jsonTree->setModel(&model);
        model.loadJson(QJsonDocument(rootObject).toJson());
    } else {
        MessageBoxWarning(tr("Json Contains Syntax Errors"),
                          tr("Original Json may contain syntax errors. Json tree is disabled."));
    }

    jsonEditor->setText(jsonToString(rootObject));
    jsonTree->expandAll();
    jsonTree->resizeColumnToContents(0);
}

QJsonObject JsonEditor::OpenEditor() {
    int resultCode = this->exec();
    auto string = jsonEditor->toPlainText();

    while (resultCode == QDialog::Accepted && !jsonError(string).isEmpty()) {
        if (string.isEmpty()) {
            resultCode = QDialog::Accepted;
            final = {};
            break;
        }
        MessageBoxWarning(tr("Json Contains Syntax Errors"),
                          tr("You must correct these errors before continuing."));
        resultCode = this->exec();
        string = jsonEditor->toPlainText();
    }

    return resultCode == QDialog::Accepted ? final : original;
}

JsonEditor::~JsonEditor() {
}

void JsonEditor::on_jsonEditor_textChanged() {
    auto string = jsonEditor->toPlainText();
    auto VerifyResult = jsonError(string);
    jsonValidateStatus->setText(VerifyResult);

    if (VerifyResult.isEmpty()) {
        markInvalid(jsonEditor, false);
        final = jsonFromString(string);
        model.loadJson(QJsonDocument(final).toJson());
        jsonTree->expandAll();
        jsonTree->resizeColumnToContents(0);
    } else {
        markInvalid(jsonEditor, true);
    }
}

void JsonEditor::on_formatJsonBtn_clicked() {
    auto string = jsonEditor->toPlainText();
    auto VerifyResult = jsonError(string);
    jsonValidateStatus->setText(VerifyResult);

    if (VerifyResult.isEmpty()) {
        markInvalid(jsonEditor, false);
        jsonEditor->setPlainText(jsonToString(jsonFromString(string)));
        model.loadJson(QJsonDocument(jsonFromString(string)).toJson());
        jsonTree->setModel(&model);
        jsonTree->expandAll();
        jsonTree->resizeColumnToContents(0);
    } else {
        markInvalid(jsonEditor, true);
        MessageBoxWarning(tr("Syntax Errors"),
                          tr("Please fix the JSON errors or remove the comments before continue"));
    }
}

void JsonEditor::on_removeCommentsBtn_clicked() {
    jsonEditor->setPlainText(jsonToString(jsonFromString(jsonEditor->toPlainText())));
}
