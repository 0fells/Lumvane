#pragma once

#include <QColor>
#include <QIcon>
#include <QPixmap>
#include <QRectF>

class QPainter;
class QWidget;

namespace Icon {

    QIcon Themed(const QString &name, const QColor &color, int size = 18);

    enum TrayIconStatus {
        NONE,
        RUNNING,
        SYSTEM_PROXY,
        VPN,
        DNS,
        SYSTEM_PROXY_DNS,
    };

    QPixmap GetTrayIcon(TrayIconStatus status);

    QIcon App();

    void PaintVane(QPainter &p, const QRectF &box, qreal swing = 0, const QColor &mono = QColor(), qreal glow = 0);

    QIcon Vane();

    void UseVaneForOtherWindows(QWidget *mainWindow);
}
