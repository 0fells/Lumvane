#pragma once

#include <QJsonObject>
#include "include/ui/widget/JsonModel.hpp"
#include "ui_JsonEditor.h"

#include <QDialog>

class JsonEditor
    : public QDialog,
      private Ui::JsonEditor {
    Q_OBJECT

public:
    explicit JsonEditor(const QJsonObject& rootObject, QWidget* parent = nullptr);
    ~JsonEditor();
    QJsonObject OpenEditor();

private slots:
    void on_jsonEditor_textChanged();

    void on_formatJsonBtn_clicked();

    void on_removeCommentsBtn_clicked();

private:
    JsonModel model;
    QJsonObject original;
    QJsonObject final;
};
