#include <QStyle>
#include <QApplication>
#include <QFile>
#include <QPalette>
#include <QColor>
#include <QStyleHints>
#include <QTimer>
#include <QWidget>

#ifdef Q_OS_WIN
#include <windows.h>
#endif

#include "include/ui/setting/ThemeManager.hpp"
#include "iostream"

ThemeManager *themeManager = new ThemeManager;

extern QString ReadFileText(const QString &path);

struct ThemeColors {
    QColor window, windowText;
    QColor base, alternateBase;
    QColor text;
    QColor button, buttonText;
    QColor brightText;
    QColor highlight, highlightedText;
    QColor link;
    QColor tooltipBase, tooltipText;
    QColor placeholder;
    QColor disabledText;
};

static QPalette buildThemePalette(const ThemeColors &c) {
    QPalette p;

    const auto setAll = [&](QPalette::ColorRole role, const QColor &col) {
        p.setColor(QPalette::Active, role, col);
        p.setColor(QPalette::Inactive, role, col);
        p.setColor(QPalette::Disabled, role, col);
    };

    setAll(QPalette::Window,          c.window);
    setAll(QPalette::WindowText,      c.windowText);
    setAll(QPalette::Base,            c.base);
    setAll(QPalette::AlternateBase,   c.alternateBase);
    setAll(QPalette::Text,            c.text);
    setAll(QPalette::Button,          c.button);
    setAll(QPalette::ButtonText,      c.buttonText);
    setAll(QPalette::BrightText,      c.brightText);
    setAll(QPalette::ToolTipBase,     c.tooltipBase);
    setAll(QPalette::ToolTipText,     c.tooltipText);
    setAll(QPalette::Highlight,       c.highlight);
    setAll(QPalette::HighlightedText, c.highlightedText);
    setAll(QPalette::Link,            c.link);
    setAll(QPalette::LinkVisited,     c.link);
    setAll(QPalette::PlaceholderText, c.placeholder);

    setAll(QPalette::Light,    c.button.lighter(130));
    setAll(QPalette::Midlight, c.button.lighter(115));
    setAll(QPalette::Mid,      c.button.darker(130));
    setAll(QPalette::Dark,     c.button.darker(160));
    setAll(QPalette::Shadow,   c.window.darker(180));

    p.setColor(QPalette::Disabled, QPalette::WindowText,      c.disabledText);
    p.setColor(QPalette::Disabled, QPalette::Text,            c.disabledText);
    p.setColor(QPalette::Disabled, QPalette::ButtonText,      c.disabledText);
    p.setColor(QPalette::Disabled, QPalette::HighlightedText, c.disabledText);
    p.setColor(QPalette::Disabled, QPalette::Link,            c.disabledText);

    return p;
}

static QPalette lumvanePalette(bool dark) {
    QPalette p = dark ? buildThemePalette({
        .window = "#12141F", .windowText = "#E8EAF4",
        .base = "#1A1D2B", .alternateBase = "#1E2131",
        .text = "#E8EAF4",
        .button = "#232738", .buttonText = "#E8EAF4",
        .brightText = "#FFFFFF",
        .highlight = "#3A2F78", .highlightedText = "#FFFFFF",
        .link = "#C3B5FF",
        .tooltipBase = "#2A2E40", .tooltipText = "#E8EAF4",
        .placeholder = "#8088A6", .disabledText = "#5C627D",
    }) : buildThemePalette({
        .window = "#F4F5FA", .windowText = "#171A2B",
        .base = "#FFFFFF", .alternateBase = "#F7F8FC",
        .text = "#171A2B",
        .button = "#FFFFFF", .buttonText = "#171A2B",
        .brightText = "#FFFFFF",
        .highlight = "#E6DEFF", .highlightedText = "#171A2B",
        .link = "#4A2CC0",
        .tooltipBase = "#171A2B", .tooltipText = "#FFFFFF",
        .placeholder = "#8A90AE", .disabledText = "#A3A8BE",
    });
    const QColor accent(dark ? "#A996FF" : "#5B3FD6");
    for (const auto group : {QPalette::Active, QPalette::Inactive}) p.setColor(group, QPalette::Accent, accent);
    for (const auto group : {QPalette::Active, QPalette::Inactive, QPalette::Disabled}) {
        p.setColor(group, QPalette::Mid, QColor(dark ? "#2A2E40" : "#DCDFEA"));
        p.setColor(group, QPalette::Dark, QColor(dark ? "#3A3F57" : "#C5C9D8"));
    }
    return p;
}

QString ThemeManager::Normalize(const QString &theme) {
    const QString t = theme.toLower();
    return t == "light" || t == "dark" ? t : QStringLiteral("system");
}

UiTokens ThemeManager::Tokens() {
    const QPalette pal = qApp->palette();
    static qint64 cachedKey = -1;
    static UiTokens cached;
    if (pal.cacheKey() == cachedKey) return cached;
    UiTokens t;
    t.dark = pal.color(QPalette::Window).lightness() < 128;
    const QColor window = pal.color(QPalette::Window);
    const QColor text = pal.color(QPalette::WindowText);
    auto mix = [&](qreal k) {
        return QColor::fromRgbF(window.redF() + (text.redF() - window.redF()) * k,
                                window.greenF() + (text.greenF() - window.greenF()) * k,
                                window.blueF() + (text.blueF() - window.blueF()) * k);
    };
    t.accent = QColor(t.dark ? "#A996FF" : "#5B3FD6");
    t.onAccent = QColor(t.dark ? "#170F3D" : "#FFFFFF");
    t.accentSoft = QColor(t.dark ? "#2E2652" : "#EEE9FF");
    t.surface = pal.color(QPalette::Base);
    t.border = mix(0.13);
    t.subtle = mix(0.06);
    t.muted = mix(0.62);
    t.good = QColor(t.dark ? "#7DD3A8" : "#1B7F55");
    t.slow = QColor(t.dark ? "#F5B35C" : "#B45309");
    t.bad = QColor(t.dark ? "#FF8A80" : "#B42318");
    cachedKey = pal.cacheKey();
    cached = t;
    return t;
}

#ifdef Q_OS_WIN
static void applyCaptionScheme(bool dark) {
    using SetAttribute = HRESULT(WINAPI *)(HWND, DWORD, LPCVOID, DWORD);
    static const auto setAttribute = reinterpret_cast<SetAttribute>(GetProcAddress(LoadLibraryW(L"dwmapi.dll"), "DwmSetWindowAttribute"));
    if (setAttribute == nullptr) return;
    constexpr DWORD kUseImmersiveDarkMode = 20;
    const BOOL value = dark;
    for (QWidget *w : QApplication::topLevelWidgets()) {
        if (!w->testAttribute(Qt::WA_WState_Created) || !w->isVisible()) continue;
        const auto hwnd = reinterpret_cast<HWND>(w->winId());
        if (!(GetWindowLongPtrW(hwnd, GWL_STYLE) & WS_CAPTION)) continue;
        setAttribute(hwnd, kUseImmersiveDarkMode, &value, sizeof(value));
        const bool active = GetActiveWindow() == hwnd;
        SendMessageW(hwnd, WM_NCACTIVATE, !active, 0);
        SendMessageW(hwnd, WM_NCACTIVATE, active, 0);
    }
}
#endif

void ThemeManager::ApplyTheme(const QString &theme, bool force) {
    const QString mode = Normalize(theme);
    const auto scheme = mode == "light" ? Qt::ColorScheme::Light : mode == "dark" ? Qt::ColorScheme::Dark : Qt::ColorScheme::Unknown;
    qApp->styleHints()->setColorScheme(scheme);
    const bool dark = mode == "dark" || (mode == "system" && qApp->styleHints()->colorScheme() == Qt::ColorScheme::Dark);
    if (!force && applied && mode == current_theme && dark == appliedDark) return;

    qApp->setStyle("Fusion");
    qApp->setPalette(lumvanePalette(dark));
    QString qss = ReadFileText(":/theme/lumvane.qss");
    const auto tk = Tokens();
    qss.replace("@border@", tk.border.name())
       .replace("@subtle@", tk.subtle.name())
       .replace("@muted@", tk.muted.name())
       .replace("@accent@", tk.accent.name())
       .replace("@onAccent@", tk.onAccent.name())
       .replace("@accentSoft@", tk.accentSoft.name())
       .replace("@surface@", tk.surface.name())
       .replace("@theme@", tk.dark ? "dark" : "light");
    qApp->setStyleSheet(qss);

#ifdef Q_OS_WIN
    if (applied) QTimer::singleShot(0, qApp, [dark] { applyCaptionScheme(dark); });
#endif
    applied = true;
    appliedDark = dark;
    current_theme = mode;
    emit themeChanged(mode);
}
