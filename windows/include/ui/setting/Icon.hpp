#pragma once

#include <QIcon>
#include <QPixmap>

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
}
