#pragma once

#include <QWidget>

class QLabel;
class QPushButton;

class EmptyProfilesView : public QWidget {
    Q_OBJECT

public:
    explicit EmptyProfilesView(QWidget *parent = nullptr);

    void applyTheme();

signals:
    void pasteRequested();
    void subscriptionRequested();
    void scanQrRequested();

private:
    QLabel *icon_;
    QLabel *title_;
    QLabel *text_;
    QPushButton *paste_;
    QPushButton *subscription_;
    QPushButton *scanQr_;
};
