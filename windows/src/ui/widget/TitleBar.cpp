#include "include/ui/widget/TitleBar.hpp"

#include <QAbstractButton>
#include <QApplication>
#include <QEvent>
#include <QHBoxLayout>
#include <QOperatingSystemVersion>
#include <QPainter>
#include <QPainterPath>
#include <QSettings>

#include "include/ui/setting/ThemeManager.hpp"

#ifdef Q_OS_WIN
#include <windows.h>
#include <windowsx.h>
#include <shellapi.h>
#ifndef ABM_GETAUTOHIDEBAREX
#define ABM_GETAUTOHIDEBAREX 0x0000000b
#endif
#endif

namespace {
    constexpr int kBarHeight = 36;
    constexpr int kButtonWidth = 46;

    bool isWindows11() {
        return QOperatingSystemVersion::current() >= QOperatingSystemVersion::Windows11;
    }

    QColor withAlpha(QColor c, qreal a) {
        c.setAlphaF(a);
        return c;
    }

#ifdef Q_OS_WIN
    QColor windows10BorderColor(bool active) {
        if (active) {
            const QSettings dwm(QStringLiteral("HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\DWM"), QSettings::NativeFormat);
            if (dwm.value(QStringLiteral("ColorPrevalence")).toInt() == 1) {
                using Fn = HRESULT(WINAPI *)(DWORD *, BOOL *);
                static const auto colorization = reinterpret_cast<Fn>(GetProcAddress(LoadLibraryW(L"dwmapi.dll"), "DwmGetColorizationColor"));
                DWORD argb = 0;
                BOOL opaque = FALSE;
                if (colorization && SUCCEEDED(colorization(&argb, &opaque))) return QColor::fromRgb(argb & 0xFFFFFF);
            }
            return QColor(0, 0, 0, 128);
        }
        return QColor(0, 0, 0, 85);
    }
#endif
}

class CaptionButton : public QAbstractButton {
public:
    enum Kind { Minimize, Maximize, Close };

    CaptionButton(Kind kind, QWidget *parent) : QAbstractButton(parent), kind_(kind) {
        setFixedSize(kButtonWidth, kBarHeight);
        setFocusPolicy(Qt::NoFocus);
        setAttribute(Qt::WA_Hover);
    }

    void setMaximized(bool maximized) {
        if (maximized_ == maximized) return;
        maximized_ = maximized;
        update();
    }

    void setNonClientHover(bool on) {
        if (ncHover_ == on) return;
        ncHover_ = on;
        if (!on) ncPressed_ = false;
        update();
    }

    void setNonClientPressed(bool on) {
        if (ncPressed_ == on) return;
        ncPressed_ = on;
        update();
    }

    bool nonClientPressed() const { return ncPressed_; }

protected:
    void paintEvent(QPaintEvent *) override {
        QPainter p(this);
        const auto tk = ThemeManager::Tokens();
        const QColor text = palette().color(QPalette::WindowText);
        const bool hovered = underMouse() || ncHover_;
        const bool pressed = isDown() || ncPressed_;
        const bool active = static_cast<TitleBar *>(parentWidget())->frameActive();

        QColor glyph = active ? text : tk.muted;
        if (kind_ == Close && (hovered || pressed)) {
            p.fillRect(rect(), pressed ? QColor(0xC4, 0x2B, 0x1C, 230) : QColor(0xC4, 0x2B, 0x1C));
            glyph = Qt::white;
        } else if (pressed) {
            p.fillRect(rect(), withAlpha(text, tk.dark ? 0.12 : 0.10));
        } else if (hovered) {
            p.fillRect(rect(), withAlpha(text, tk.dark ? 0.08 : 0.06));
        }

        static const ushort glyphs[] = {0xE921, 0xE922, 0xE8BB};
        ushort code = glyphs[kind_];
        if (kind_ == Maximize && maximized_) code = 0xE923;
        QFont f;
        f.setFamilies({QStringLiteral("Segoe Fluent Icons"), QStringLiteral("Segoe MDL2 Assets")});
        f.setPixelSize(10);
        p.setFont(f);
        p.setPen(glyph);
        p.drawText(rect(), Qt::AlignCenter, QString(QChar(code)));
    }

private:
    Kind kind_;
    bool maximized_ = false;
    bool ncHover_ = false;
    bool ncPressed_ = false;
};

TitleBar::TitleBar(QWidget *window) : QWidget(window), window_(window) {
    setFixedHeight(kBarHeight);
    setAttribute(Qt::WA_StyledBackground, false);

    minimize_ = new CaptionButton(CaptionButton::Minimize, this);
    maximize_ = new CaptionButton(CaptionButton::Maximize, this);
    close_ = new CaptionButton(CaptionButton::Close, this);
    minimize_->setAccessibleName(tr("Minimize"));
    maximize_->setAccessibleName(tr("Maximize"));
    close_->setAccessibleName(tr("Close"));

    auto *layout = new QHBoxLayout(this);
    layout->setContentsMargins(0, 0, 0, 0);
    layout->setSpacing(0);
    layout->addStretch();
    layout->addWidget(minimize_);
    layout->addWidget(maximize_);
    layout->addWidget(close_);

#ifdef Q_OS_WIN
    const auto sysCommand = [this](WPARAM command) {
        PostMessageW(reinterpret_cast<HWND>(window_->winId()), WM_SYSCOMMAND, command, 0);
    };
    connect(minimize_, &QAbstractButton::clicked, this, [=] { sysCommand(SC_MINIMIZE); });
    connect(maximize_, &QAbstractButton::clicked, this, [=, this] {
        sysCommand(window_->isMaximized() ? SC_RESTORE : SC_MAXIMIZE);
    });
#endif
    connect(close_, &QAbstractButton::clicked, window_, &QWidget::close);
    connect(window_, &QWidget::windowTitleChanged, this, [this] { update(); });
    window_->installEventFilter(this);
    hide();
}

void TitleBar::setCustomFrame(bool on) {
#ifdef Q_OS_WIN
    if (on == customFrame_) return;
    customFrame_ = on;
    setVisible(on);
    updateMaximizeButton();
    const HWND hwnd = reinterpret_cast<HWND>(window_->winId());
    const LONG_PTR style = GetWindowLongPtrW(hwnd, GWL_STYLE);
    const LONG_PTR frame = WS_THICKFRAME | WS_SYSMENU | WS_MINIMIZEBOX | WS_MAXIMIZEBOX;
    SetWindowLongPtrW(hwnd, GWL_STYLE, on ? (style & ~LONG_PTR(WS_CAPTION)) | frame : style | WS_CAPTION);
    SetWindowPos(hwnd, nullptr, 0, 0, 0, 0,
                 SWP_FRAMECHANGED | SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_NOOWNERZORDER | SWP_NOACTIVATE);
#else
    Q_UNUSED(on)
#endif
}

void TitleBar::applyTheme() {
    update();
    minimize_->update();
    maximize_->update();
    close_->update();
}

void TitleBar::updateMaximizeButton() {
    maximize_->setMaximized(window_->isMaximized());
    maximize_->setAccessibleName(window_->isMaximized() ? tr("Restore") : tr("Maximize"));
}

bool TitleBar::eventFilter(QObject *watched, QEvent *event) {
    if (watched == window_ && event->type() == QEvent::WindowStateChange) updateMaximizeButton();
    if (watched == window_ && event->type() == QEvent::ActivationChange) applyTheme();
    return QWidget::eventFilter(watched, event);
}

void TitleBar::changeEvent(QEvent *event) {
    if (event->type() == QEvent::ActivationChange) applyTheme();
    QWidget::changeEvent(event);
}

void TitleBar::paintEvent(QPaintEvent *) {
    QPainter p(this);
    p.setRenderHint(QPainter::Antialiasing);
    const auto tk = ThemeManager::Tokens();
    p.fillRect(rect(), tk.surface);

    const QRectF logo(8, (height() - 28) / 2.0, 28, 28);
#ifdef Q_OS_WIN
    if (!isWindows11() && !window_->isMaximized()) {
        p.fillRect(QRectF(0, 0, width(), 1.0 / devicePixelRatioF()), windows10BorderColor(frameActive()));
    }
#endif
    const QColor tail("#5C7CFF"), shaft("#6A4FE6"), head("#A68BFF");
    p.save();
    p.translate(logo.center());
    p.scale(logo.width() / 390.0, logo.width() / 390.0);
    p.translate(-253, -268);
    p.translate(256, 256);
    p.rotate(-30);
    p.translate(-256, -256);
    p.setPen(Qt::NoPen);
    p.setBrush(tail);
    p.drawPolygon(QPolygonF({{64, 214}, {150, 214}, {176, 256}, {150, 298}, {64, 298}, {98, 256}}));
    p.setBrush(shaft);
    p.drawRoundedRect(QRectF(150, 240, 220, 32), 16, 16);
    p.setBrush(head);
    p.drawPolygon(QPolygonF({{352, 192}, {460, 256}, {352, 320}}));
    p.setBrush(tk.surface);
    p.setPen(QPen(shaft, 18));
    p.drawEllipse(QPointF(256, 256), 34, 34);
    p.restore();

    const int textLeft = qRound(logo.right()) + 10;
    const QRect textRect(textLeft, 0, minimize_->x() - textLeft - 8, height());
    p.setPen(frameActive() ? tk.muted : withAlpha(tk.muted, 0.7));
    p.setFont(font());
    p.drawText(textRect, Qt::AlignVCenter | Qt::AlignLeft,
               fontMetrics().elidedText(window_->windowTitle(), Qt::ElideRight, textRect.width()));
}

#ifdef Q_OS_WIN
namespace {
    UINT windowDpi(HWND hwnd) {
        using Fn = UINT(WINAPI *)(HWND);
        static const auto fn = reinterpret_cast<Fn>(GetProcAddress(GetModuleHandleW(L"user32"), "GetDpiForWindow"));
        return fn ? fn(hwnd) : 96;
    }

    int frameThickness(HWND hwnd) {
        using Fn = BOOL(WINAPI *)(LPRECT, DWORD, BOOL, DWORD, UINT);
        static const auto adjustForDpi = reinterpret_cast<Fn>(GetProcAddress(GetModuleHandleW(L"user32"), "AdjustWindowRectExForDpi"));
        RECT rc{0, 0, 0, 0};
        const DWORD style = DWORD(GetWindowLongPtrW(hwnd, GWL_STYLE)) & ~DWORD(WS_CAPTION);
        const DWORD exStyle = DWORD(GetWindowLongPtrW(hwnd, GWL_EXSTYLE));
        if (adjustForDpi) adjustForDpi(&rc, style, FALSE, exStyle, windowDpi(hwnd));
        else AdjustWindowRectEx(&rc, style, FALSE, exStyle);
        return -rc.left;
    }

    void reserveAutoHideTaskbar(const MONITORINFO &mi, RECT &rc) {
        APPBARDATA state{};
        state.cbSize = sizeof(state);
        if (!(SHAppBarMessage(ABM_GETSTATE, &state) & ABS_AUTOHIDE)) return;
        const auto onEdge = [&](UINT edge) {
            APPBARDATA bar{};
            bar.cbSize = sizeof(bar);
            bar.uEdge = edge;
            bar.rc = mi.rcMonitor;
            return SHAppBarMessage(ABM_GETAUTOHIDEBAREX, &bar) != 0;
        };
        if (onEdge(ABE_BOTTOM)) rc.bottom -= 2;
        else if (onEdge(ABE_TOP)) rc.top += 2;
        else if (onEdge(ABE_LEFT)) rc.left += 2;
        else if (onEdge(ABE_RIGHT)) rc.right -= 2;
    }
}
#endif

bool TitleBar::handleNativeEvent(void *message, qintptr *result) {
#ifdef Q_OS_WIN
    if (!customFrame_) return false;
    MSG *msg = static_cast<MSG *>(message);
    const HWND hwnd = msg->hwnd;
    switch (msg->message) {
    case WM_STYLECHANGING:
        if (msg->wParam == WPARAM(GWL_STYLE)) reinterpret_cast<STYLESTRUCT *>(msg->lParam)->styleNew &= ~DWORD(WS_CAPTION);
        return false;
    case WM_GETMINMAXINFO: {
        MONITORINFO mi{};
        mi.cbSize = sizeof(mi);
        if (GetMonitorInfoW(MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST), &mi)) {
            auto *mmi = reinterpret_cast<MINMAXINFO *>(msg->lParam);
            RECT work = mi.rcWork;
            reserveAutoHideTaskbar(mi, work);
            const int frame = frameThickness(hwnd);
            mmi->ptMaxPosition.x = work.left - mi.rcMonitor.left - frame;
            mmi->ptMaxPosition.y = work.top - mi.rcMonitor.top - frame;
            mmi->ptMaxSize.x = work.right - work.left + 2 * frame;
            mmi->ptMaxSize.y = work.bottom - work.top + 2 * frame;
        }
        return false;
    }
    case WM_WINDOWPOSCHANGING: {
        auto *pos = reinterpret_cast<WINDOWPOS *>(msg->lParam);
        if ((pos->flags & SWP_NOSIZE) || !IsZoomed(hwnd)) return false;
        const RECT proposed{pos->x, pos->y, pos->x + pos->cx, pos->y + pos->cy};
        MONITORINFO mi{};
        mi.cbSize = sizeof(mi);
        if (!GetMonitorInfoW(MonitorFromRect(&proposed, MONITOR_DEFAULTTONEAREST), &mi)) return false;
        RECT work = mi.rcWork;
        reserveAutoHideTaskbar(mi, work);
        const int frame = frameThickness(hwnd);
        pos->x = work.left - frame;
        pos->y = work.top - frame;
        pos->cx = work.right - work.left + 2 * frame;
        pos->cy = work.bottom - work.top + 2 * frame;
        pos->flags &= ~UINT(SWP_NOMOVE);
        return false;
    }
    case WM_NCCALCSIZE: {
        if (!msg->wParam || IsIconic(hwnd)) return false;
        auto *params = reinterpret_cast<NCCALCSIZE_PARAMS *>(msg->lParam);
        const LONG top = params->rgrc[0].top;
        *result = DefWindowProcW(hwnd, WM_NCCALCSIZE, msg->wParam, msg->lParam);
        RECT &client = params->rgrc[0];
        client.top = top + (IsZoomed(hwnd) ? frameThickness(hwnd) : 0);
        return true;
    }
    case WM_NCACTIVATE:
        *result = DefWindowProcW(hwnd, WM_NCACTIVATE, msg->wParam, -1);
        frameActive_ = msg->wParam != FALSE;
        applyTheme();
        return true;
    case WM_NCHITTEST: {
        const LRESULT hit = DefWindowProcW(hwnd, WM_NCHITTEST, msg->wParam, msg->lParam);
        if (hit != HTCLIENT) {
            *result = hit;
            return true;
        }
        POINT pt{GET_X_LPARAM(msg->lParam), GET_Y_LPARAM(msg->lParam)};
        ScreenToClient(hwnd, &pt);
        if (!IsZoomed(hwnd)) {
            RECT client;
            GetClientRect(hwnd, &client);
            const int frame = frameThickness(hwnd);
            if (pt.y < frame) {
                *result = pt.x < frame * 2 ? HTTOPLEFT : pt.x >= client.right - frame * 2 ? HTTOPRIGHT : HTTOP;
                return true;
            }
        }
        const QPoint local = mapFrom(window_, QPointF(pt.x, pt.y) / window_->devicePixelRatioF()).toPoint();
        if (!rect().contains(local)) return false;
        if (maximize_->geometry().contains(local)) {
            *result = isWindows11() ? HTMAXBUTTON : HTCLIENT;
            return true;
        }
        if (minimize_->geometry().contains(local) || close_->geometry().contains(local)) {
            *result = HTCLIENT;
            return true;
        }
        *result = HTCAPTION;
        return true;
    }
    case WM_NCMOUSEMOVE: {
        const bool over = msg->wParam == HTMAXBUTTON;
        maximize_->setNonClientHover(over);
        if (over) {
            TRACKMOUSEEVENT tme{sizeof(tme), TME_LEAVE | TME_NONCLIENT, hwnd, 0};
            TrackMouseEvent(&tme);
        }
        return false;
    }
    case WM_NCMOUSELEAVE:
    case WM_MOUSEMOVE:
        maximize_->setNonClientHover(false);
        return false;
    case WM_NCLBUTTONDOWN:
    case WM_NCLBUTTONDBLCLK:
        if (msg->wParam != HTMAXBUTTON) return false;
        maximize_->setNonClientPressed(true);
        *result = 0;
        return true;
    case WM_NCLBUTTONUP:
        if (msg->wParam != HTMAXBUTTON) return false;
        if (maximize_->nonClientPressed()) {
            maximize_->setNonClientPressed(false);
            maximize_->click();
        }
        *result = 0;
        return true;
    default:
        return false;
    }
#else
    Q_UNUSED(message)
    Q_UNUSED(result)
    return false;
#endif
}
