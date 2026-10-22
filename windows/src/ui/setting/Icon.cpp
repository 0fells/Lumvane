#include "include/ui/setting/Icon.hpp"

#include "include/global/Configs.hpp"

#include <QApplication>
#include <QBuffer>
#include <QEvent>
#include <QFile>
#include <QImageReader>
#include <QPainter>
#include <QPainterPath>
#include <QRadialGradient>

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

void Icon::PaintVane(QPainter &p, const QRectF &box, qreal swing, const QColor &mono, qreal glow) {
    QColor tail("#5C7CFF"), shaft("#6A4FE6"), head("#A68BFF");
    if (mono.isValid()) {
        shaft = mono;
        tail = mono;
        tail.setAlphaF(mono.alphaF() * 0.55);
        head = mono;
        head.setAlphaF(mono.alphaF() * 0.8);
    }
    p.save();
    p.setRenderHint(QPainter::Antialiasing);
    p.translate(box.center());
    p.scale(box.width() / 390.0, box.width() / 390.0);
    p.translate(-253, -268);
    p.translate(256, 256);
    p.rotate(-30 + swing);
    p.translate(-256, -256);
    if (glow > 0) {
        QColor lit("#A68BFF"), clear("#A68BFF");
        lit.setAlphaF(0.6 * glow);
        clear.setAlphaF(0);
        QRadialGradient halo(QPointF(392, 256), 140);
        halo.setColorAt(0, lit);
        halo.setColorAt(1, clear);
        p.setPen(Qt::NoPen);
        p.setBrush(halo);
        p.drawEllipse(QPointF(392, 256), 140, 140);
    }
    p.setPen(Qt::NoPen);
    p.setBrush(tail);
    p.drawPolygon(QPolygonF({{64, 214}, {150, 214}, {176, 256}, {150, 298}, {64, 298}, {98, 256}}));
    p.setBrush(shaft);
    p.drawRect(QRectF(162, 240, 64, 32));
    p.drawRect(QRectF(286, 240, 70, 32));
    p.setBrush(head);
    p.drawPolygon(QPolygonF({{352, 192}, {460, 256}, {352, 320}}));
    QPainterPath ring;
    ring.setFillRule(Qt::OddEvenFill);
    ring.addEllipse(QPointF(256, 256), 43, 43);
    ring.addEllipse(QPointF(256, 256), 25, 25);
    p.fillPath(ring, shaft);
    p.restore();
}

QIcon Icon::Vane() {
    QIcon icon;
    for (const int px : {16, 20, 24, 32, 40, 48, 64, 128}) {
        QPixmap pm(px, px);
        pm.fill(Qt::transparent);
        QPainter p(&pm);
        PaintVane(p, QRectF(0, 0, px, px));
        p.end();
        icon.addPixmap(pm);
    }
    return icon;
}

namespace {
    class VaneIconFilter : public QObject {
    public:
        VaneIconFilter(QWidget *mainWindow, QObject *parent) : QObject(parent), mainWindow_(mainWindow) {}

        bool eventFilter(QObject *obj, QEvent *event) override {
            if (event->type() != QEvent::Show || !obj->isWidgetType()) return false;
            auto *w = static_cast<QWidget *>(obj);
            const auto type = w->windowType();
            if (w == mainWindow_ || !w->isWindow() || (type != Qt::Window && type != Qt::Dialog)) return false;
            if (w->property(kDone).toBool()) return false;
            static const QIcon vane = Icon::Vane();
            w->setProperty(kDone, true);
            w->setWindowIcon(vane);
            return false;
        }

    private:
        static constexpr const char *kDone = "lumvaneVaneIcon";
        QWidget *mainWindow_;
    };
}

void Icon::UseVaneForOtherWindows(QWidget *mainWindow) {
    qApp->installEventFilter(new VaneIconFilter(mainWindow, qApp));
}
