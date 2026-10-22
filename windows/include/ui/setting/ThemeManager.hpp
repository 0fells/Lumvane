#pragma once

#include <QColor>
#include <QObject>
#include <QPalette>

struct UiTokens {
    bool dark = false;
    QColor accent, onAccent, accentSoft;
    QColor surface, border, muted, subtle;
    QColor good, slow, bad;
};

class ThemeManager : public QObject {
    Q_OBJECT
public:
    QString current_theme;

    void ApplyTheme(const QString &theme, bool force = false);

    static QString Normalize(const QString &theme);
    static UiTokens Tokens();

private:
    bool applied = false;
    bool appliedDark = false;
signals:
    void themeChanged(QString themeName);
};

extern ThemeManager *themeManager;
