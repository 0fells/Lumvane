#pragma once

#include <QDialog>
#include <QHash>
#include <QPair>
#include <QPlainTextEdit>
#include <memory>

#include "include/database/entities/RouteProfile.h"

class QLineEdit;
class QCheckBox;
class QCompleter;
class QLabel;

class RawRouteEdit : public QPlainTextEdit {
    Q_OBJECT

public:
    explicit RawRouteEdit(QWidget* parent = nullptr);
    void setOutboundItems(const QList<QPair<QString, QString>>& items);

protected:
    void keyPressEvent(QKeyEvent* e) override;

private slots:
    void insertOutboundCompletion(const QString& completion);

private:
    [[nodiscard]] bool outboundContext(QString* partial) const;
    void updateCompleter();
    bool handleAutoEdit(QKeyEvent* e);
    [[nodiscard]] QChar charBeforeCursor() const;
    [[nodiscard]] QChar charAfterCursor() const;
    QCompleter* completer = nullptr;
    QHash<QString, QString> outboundIdByDisplay;
};

class RawRouteItem : public QDialog {
    Q_OBJECT

public:
    explicit RawRouteItem(QWidget* parent = nullptr, const std::shared_ptr<Configs::RouteProfile>& routeChain = nullptr);

    std::shared_ptr<Configs::RouteProfile> chain;

signals:
    void settingsChanged(std::shared_ptr<Configs::RouteProfile> routingChain);

private slots:
    void accept() override;

private:
    QLineEdit* nameEdit = nullptr;
    RawRouteEdit* jsonEdit = nullptr;
    QCheckBox* preventCheck = nullptr;
    QLabel* validateLabel = nullptr;
};
