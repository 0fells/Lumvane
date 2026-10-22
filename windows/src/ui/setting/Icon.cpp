#include "include/ui/setting/Icon.hpp"

#include "include/global/Configs.hpp"

#include <QBuffer>
#include <QFile>
#include <QImageReader>
#include <QPainter>

QIcon Icon::Themed(const QString &name, const QColor &color, int size) {
    QFile f(":/icon/ui/" + name + ".svg");
    if (!f.open(QIODevice::ReadOnly)) return {};
    QByteArray svg = f.readAll();
    svg.replace("#000000", color.name(QColor::HexRgb).toLatin1());

    QIcon icon;
    for (const qreal scale : {1.0, 1.25, 1.5, 2.0}) {
        QBuffer buf(&svg);
        QImageReader reader(&buf, "svg");
        const int px = qRound(size * scale);
        reader.setScaledSize(QSize(px, px));
        const QImage img = reader.read();
        if (!img.isNull()) icon.addPixmap(QPixmap::fromImage(img));
    }
    return icon;
}


QPixmap Icon::GetTrayIcon(TrayIconStatus status) {
    QPixmap pixmap;
    QPixmap pixmap_read;

    if (status == NONE)
    {
        if (Configs::dataManager->settingsRepo->use_custom_icons) {
            pixmap_read = QPixmap(QString("icons/") + "Off" + ".png");
        }
        if (pixmap_read.isNull()) {
            pixmap_read = QPixmap(QString(":/Lumvane/") + "Off" + ".png");
        }
        if (!pixmap_read.isNull()) pixmap = pixmap_read;
    } else if (status == RUNNING)
    {
        if (Configs::dataManager->settingsRepo->use_custom_icons) {
            pixmap_read = QPixmap(QString("icons/") + "Running" + ".png");
        }
        if (pixmap_read.isNull()) {
            pixmap_read = QPixmap(QString(":/Lumvane/") + "Running" + ".png");
        }
        if (!pixmap_read.isNull()) pixmap = pixmap_read;
    } else if (status == SYSTEM_PROXY_DNS)
    {
        if (Configs::dataManager->settingsRepo->use_custom_icons) {
            pixmap_read = QPixmap(QString("icons/") + "Proxy-Dns" + ".png");
        }
        if (pixmap_read.isNull()) {
            pixmap_read = QPixmap(QString(":/Lumvane/") + "Proxy-Dns" + ".png");
        }
        if (!pixmap_read.isNull()) pixmap = pixmap_read;
    } else if (status == SYSTEM_PROXY)
    {
        if (Configs::dataManager->settingsRepo->use_custom_icons) {
            pixmap_read = QPixmap(QString("icons/") + "Proxy" + ".png");
        }
        if (pixmap_read.isNull()) {
            pixmap_read = QPixmap(QString(":/Lumvane/") + "Proxy" + ".png");
        }
        if (!pixmap_read.isNull()) pixmap = pixmap_read;
    } else if (status == DNS)
    {
        if (Configs::dataManager->settingsRepo->use_custom_icons) {
            pixmap_read = QPixmap(QString("icons/") + "Dns" + ".png");
        }
        if (pixmap_read.isNull()) {
            pixmap_read = QPixmap(QString(":/Lumvane/") + "Dns" + ".png");
        }
        if (!pixmap_read.isNull()) pixmap = pixmap_read;
    } else if (status == VPN)
    {
        if (Configs::dataManager->settingsRepo->use_custom_icons) {
            pixmap_read = QPixmap(QString("icons/") + "Tun" + ".png");
        }
        if (pixmap_read.isNull()) {
            pixmap_read = QPixmap(QString(":/Lumvane/") + "Tun" + ".png");
        }
        if (!pixmap_read.isNull()) pixmap = pixmap_read;
    } else
    {
        MW_show_log("Icon::GetTrayIcon: Unknown status");
        if (Configs::dataManager->settingsRepo->use_custom_icons) {
            pixmap_read = QPixmap(QString("icons/") + "Off" + ".png");
        }
        if (pixmap_read.isNull()) {
            pixmap_read = QPixmap(QString(":/Lumvane/") + "Off" + ".png");
        }
        if (!pixmap_read.isNull()) pixmap = pixmap_read;
    }

    return pixmap;
}

QIcon Icon::App() {
    QIcon icon;
    icon.addFile(":/Lumvane/App-32.png");
    icon.addFile(":/Lumvane/App.png");
    return icon;
}

QIcon Icon::Mark() {
    QIcon icon;
    for (const int px : {24, 48, 128}) icon.addFile(QStringLiteral(":/Lumvane/Mark-%1.png").arg(px));
    return icon;
}
