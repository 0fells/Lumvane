#pragma once

#include <QObject>
#include <QKeySequence>
#include <QPair>
#include <QLoggingCategory>

#ifdef QHOTKEY_SHARED
#	ifdef QHOTKEY_LIBRARY
#		define QHOTKEY_EXPORT Q_DECL_EXPORT
#	else
#		define QHOTKEY_EXPORT Q_DECL_IMPORT
#	endif
#else
#	define QHOTKEY_EXPORT
#endif

#define QHOTKEY_HASH_SEED size_t

class QHOTKEY_EXPORT QHotkey : public QObject
{
	Q_OBJECT
	friend class QHotkeyPrivate;

	Q_PROPERTY(bool registered READ isRegistered WRITE setRegistered NOTIFY registeredChanged)
	Q_PROPERTY(QKeySequence shortcut READ shortcut WRITE setShortcut RESET resetShortcut)

public:
	class QHOTKEY_EXPORT NativeShortcut {
	public:
		quint32 key;
		quint32 modifier;

		NativeShortcut();
		NativeShortcut(quint32 key, quint32 modifier = 0);

		bool isValid() const;

		bool operator ==(NativeShortcut other) const;
		bool operator !=(NativeShortcut other) const;

	private:
		bool valid;
	};

	static void addGlobalMapping(const QKeySequence &shortcut, NativeShortcut nativeShortcut);

	static bool isPlatformSupported();

	explicit QHotkey(QObject *parent = nullptr);
	explicit QHotkey(const QKeySequence &shortcut, bool autoRegister = false, QObject *parent = nullptr);
	explicit QHotkey(Qt::Key keyCode, Qt::KeyboardModifiers modifiers, bool autoRegister = false, QObject *parent = nullptr);
	explicit QHotkey(NativeShortcut shortcut, bool autoRegister = false, QObject *parent = nullptr);
	~QHotkey() override;

	bool isRegistered() const;
	QKeySequence shortcut() const;
	Qt::Key keyCode() const;
	Qt::KeyboardModifiers modifiers() const;

	NativeShortcut currentNativeShortcut() const;

public slots:
	bool setRegistered(bool registered);

	bool setShortcut(const QKeySequence &shortcut, bool autoRegister = false);
	bool setShortcut(Qt::Key keyCode, Qt::KeyboardModifiers modifiers, bool autoRegister = false);
	bool resetShortcut();

	bool setNativeShortcut(QHotkey::NativeShortcut nativeShortcut, bool autoRegister = false);

signals:
	void activated(QPrivateSignal);

	void released(QPrivateSignal);

	void registeredChanged(bool registered);

private:
	Qt::Key _keyCode;
	Qt::KeyboardModifiers _modifiers;

	NativeShortcut _nativeShortcut;
	bool _registered;
};

QHOTKEY_HASH_SEED QHOTKEY_EXPORT qHash(QHotkey::NativeShortcut key);
QHOTKEY_HASH_SEED QHOTKEY_EXPORT qHash(QHotkey::NativeShortcut key, QHOTKEY_HASH_SEED seed);

QHOTKEY_EXPORT Q_DECLARE_LOGGING_CATEGORY(logQHotkey)

Q_DECLARE_METATYPE(QHotkey::NativeShortcut)