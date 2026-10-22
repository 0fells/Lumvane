#pragma once

#include <QString>
#include <QStringList>

namespace Logging {
    enum class Level {
        Trace = 0,
        Debug = 1,
        Info = 2,
        Warn = 3,
        Error = 4,
        Fatal = 5,
        Off = 6,
    };

    Level LevelFromString(const QString &name);

    QString LevelToString(Level level);

    void InstallQtMessageHandler();

    void Init(const QString &baseDir);

    void Shutdown();

    void SetLevel(Level level);

    Level GetLevel();

    bool IsEnabled(Level level);

    void Write(Level level, const QString &message, const char *file = nullptr, int line = 0);

    void WriteUserLog(const QString &message);

    constexpr int MAX_RECENT_LINES = 400;

    int RecentLinesRaw(const QByteArray **out, int max);

    QStringList RecentLines(int max = 0);

    void FlushForCrash();

    bool PreviousSessionCrashed();

    QString PreviousSessionLogPath();

    QString LogDir();

    QString CrashDir();

#ifdef Q_OS_WIN
    const wchar_t *CrashDirNative();
#endif
}

#define LOG_TRACE(msg) ::Logging::Write(::Logging::Level::Trace, (msg), __FILE__, __LINE__)
#define LOG_DEBUG(msg) ::Logging::Write(::Logging::Level::Debug, (msg), __FILE__, __LINE__)
#define LOG_INFO(msg)  ::Logging::Write(::Logging::Level::Info,  (msg), __FILE__, __LINE__)
#define LOG_WARN(msg)  ::Logging::Write(::Logging::Level::Warn,  (msg), __FILE__, __LINE__)
#define LOG_ERROR(msg) ::Logging::Write(::Logging::Level::Error, (msg), __FILE__, __LINE__)
#define LOG_FATAL(msg) ::Logging::Write(::Logging::Level::Fatal, (msg), __FILE__, __LINE__)
