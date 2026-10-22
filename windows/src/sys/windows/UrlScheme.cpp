#include "include/sys/UrlScheme.hpp"

#include <QApplication>
#include <QDir>
#include <QFileInfo>
#include <QSettings>

#include <shlobj.h>

static const QString kClasses = "HKEY_CURRENT_USER\\Software\\Classes";
static const QString kProgId = "Lumvane.Config";

static const QStringList kConfigExtensions = {".json", ".conf", ".yaml", ".yml", ".ini", ".txt"};

static QString openCommand() {
    return "\"" + QDir::toNativeSeparators(QApplication::applicationFilePath()) + "\" \"%1\"";
}

QString UrlScheme_DesiredState() {
    return "v2|" + openCommand();
}

void UrlScheme_Apply() {
    const QString command = openCommand();
    const QString exe = QDir::toNativeSeparators(QApplication::applicationFilePath());

    QSettings scheme(kClasses + "\\lumvane", QSettings::NativeFormat);
    scheme.setValue("Default", "URL:Lumvane Protocol");
    scheme.setValue("URL Protocol", "");
    scheme.setValue("shell/open/command/Default", command);

    QSettings progId(kClasses + "\\" + kProgId, QSettings::NativeFormat);
    progId.setValue("Default", "Lumvane profile");
    progId.setValue("DefaultIcon/Default", exe + ",0");
    progId.setValue("shell/open/command/Default", command);

    for (const QString &ext : kConfigExtensions) {
        QSettings assoc(kClasses + "\\" + ext + "\\OpenWithProgids", QSettings::NativeFormat);
        assoc.setValue(kProgId, "");
    }

    QSettings app(kClasses + "\\Applications\\" + QFileInfo(exe).fileName(), QSettings::NativeFormat);
    app.setValue("FriendlyAppName", "Lumvane");
    app.setValue("shell/open/command/Default", command);
    for (const QString &ext : kConfigExtensions) {
        app.setValue("SupportedTypes/" + ext, "");
    }

    scheme.sync();
    progId.sync();
    app.sync();
    SHChangeNotify(SHCNE_ASSOCCHANGED, SHCNF_IDLIST, nullptr, nullptr);
}
