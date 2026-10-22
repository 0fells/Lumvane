/**
 * @file    Database.h
 * @ingroup SQLiteCpp
 * @brief   Management of a SQLite Database Connection.
 *
 * Copyright (c) 2012-2025 Sebastien Rombauts (sebastien.rombauts@gmail.com)
 *
 * Distributed under the MIT License (MIT) (See accompanying file LICENSE.txt
 * or copy at http://opensource.org/licenses/MIT)
 */
#pragma once

#include <3rdparty/SQLiteCpp/include/SQLiteCppExport.h>
#include <3rdparty/SQLiteCpp/include/Column.h>

#ifndef SQLITECPP_HAVE_STD_EXPERIMENTAL_FILESYSTEM
#if __cplusplus >= 201703L
    #if defined(__MINGW32__) || defined(__MINGW64__)
        #if __GNUC__ > 8
            #define SQLITECPP_HAVE_STD_FILESYSTEM
        #endif
    #elif defined(__MAC_OS_X_VERSION_MIN_REQUIRED) && __MAC_OS_X_VERSION_MIN_REQUIRED < 101500
    #elif defined(__IPHONE_OS_VERSION_MIN_REQUIRED) && defined(__IPHONE_13_0) && \
__IPHONE_OS_VERSION_MIN_REQUIRED < __IPHONE_13_0
    #else
        #define SQLITECPP_HAVE_STD_FILESYSTEM
    #endif
#elif defined(_MSVC_LANG) && _MSVC_LANG >= 201703L
    #define SQLITECPP_HAVE_STD_FILESYSTEM
#endif

#ifdef __has_include
    #if !__has_include(<filesystem>)
        #undef SQLITECPP_HAVE_STD_FILESYSTEM
    #endif
    #if !__has_include(<experimental/filesystem>)
        #undef SQLITECPP_HAVE_EXPERIMENTAL_FILESYSTEM
    #endif
#endif

#ifdef SQLITECPP_DISABLE_STD_FILESYSTEM
    #undef SQLITECPP_HAVE_STD_FILESYSTEM
    #undef SQLITECPP_HAVE_STD_EXPERIMENTAL_FILESYSTEM
#endif

#ifdef SQLITECPP_HAVE_STD_FILESYSTEM
#include  <filesystem>
#endif

#else

#define SQLITECPP_HAVE_STD_FILESYSTEM
#include  <experimental/filesystem>
namespace std {
namespace filesystem = experimental::filesystem;
}

#endif

#include <memory>
#include <string.h>

struct sqlite3;
struct sqlite3_context;

#ifndef SQLITE_USE_LEGACY_STRUCT
typedef struct sqlite3_value sqlite3_value;
#else
struct Mem;
typedef struct Mem sqlite3_value;
#endif


namespace SQLite
{

SQLITECPP_API extern const int OPEN_READONLY;
SQLITECPP_API extern const int OPEN_READWRITE;
SQLITECPP_API extern const int OPEN_CREATE;
SQLITECPP_API extern const int OPEN_URI;
SQLITECPP_API extern const int OPEN_MEMORY;
SQLITECPP_API extern const int OPEN_NOMUTEX;
SQLITECPP_API extern const int OPEN_FULLMUTEX;
SQLITECPP_API extern const int OPEN_SHAREDCACHE;
SQLITECPP_API extern const int OPEN_PRIVATECACHE;
SQLITECPP_API extern const int OPEN_NOFOLLOW;


SQLITECPP_API extern const int OK;

SQLITECPP_API extern const char* const VERSION;
SQLITECPP_API extern const int         VERSION_NUMBER;

SQLITECPP_API const char* getLibVersion() noexcept;
SQLITECPP_API int   getLibVersionNumber() noexcept;

struct Header {
    unsigned char headerStr[16];
    unsigned int pageSizeBytes;
    unsigned char fileFormatWriteVersion;
    unsigned char fileFormatReadVersion;
    unsigned char reservedSpaceBytes;
    unsigned char maxEmbeddedPayloadFrac;
    unsigned char minEmbeddedPayloadFrac;
    unsigned char leafPayloadFrac;
    unsigned long fileChangeCounter;
    unsigned long  databaseSizePages;
    unsigned long firstFreelistTrunkPage;
    unsigned long totalFreelistPages;
    unsigned long schemaCookie;
    unsigned long schemaFormatNumber;
    unsigned long defaultPageCacheSizeBytes;
    unsigned long largestBTreePageNumber;
    unsigned long databaseTextEncoding;
    unsigned long userVersion;
    unsigned long incrementalVaccumMode;
    unsigned long applicationId;
    unsigned long versionValidFor;
    unsigned long sqliteVersion;
};

class SQLITECPP_API Database
{
    friend class Statement;

public:
    Database(const char* apFilename,
             const int   aFlags         = SQLite::OPEN_READONLY,
             const int   aBusyTimeoutMs = 0,
             const char* apVfs          = nullptr);

    Database(const std::string& aFilename,
             const int          aFlags          = SQLite::OPEN_READONLY,
             const int          aBusyTimeoutMs  = 0,
             const std::string& aVfs            = "") :
        Database(aFilename.c_str(), aFlags, aBusyTimeoutMs, aVfs.empty() ? nullptr : aVfs.c_str())
    {
    }

    #ifdef SQLITECPP_HAVE_STD_FILESYSTEM

    Database(const std::filesystem::path& apFilename,
             const int   aFlags         = SQLite::OPEN_READONLY,
             const int   aBusyTimeoutMs = 0,
             const std::string& aVfs            = "") :
        Database(reinterpret_cast<const char*>(apFilename.u8string().c_str()),
                 aFlags, aBusyTimeoutMs, aVfs.empty() ? nullptr : aVfs.c_str())
    {
    }

    #endif

    Database(const Database&) = delete;
    Database& operator=(const Database&) = delete;

    Database(Database&& aDatabase) = default;
    Database& operator=(Database&& aDatabase) = default;

    ~Database() = default;

    struct Deleter
    {
        SQLITECPP_API void operator()(sqlite3* apSQLite);
    };

    void setBusyTimeout(const int aBusyTimeoutMs);

    int exec(const char* apQueries);

    int exec(const std::string& aQueries)
    {
        return exec(aQueries.c_str());
    }

    int tryExec(const char* apQueries) noexcept;

    int tryExec(const std::string& aQueries) noexcept
    {
        return tryExec(aQueries.c_str());
    }

    Column execAndGet(const char* apQuery);

    Column execAndGet(const std::string& aQuery)
    {
        return execAndGet(aQuery.c_str());
    }

    bool tableExists(const char* apTableName) const;

    bool tableExists(const std::string& aTableName) const
    {
        return tableExists(aTableName.c_str());
    }

    int64_t getLastInsertRowid() const noexcept;

    int getChanges() const noexcept;

    int getTotalChanges() const noexcept;

    int getErrorCode() const noexcept;
    int getExtendedErrorCode() const noexcept;
    const char* getErrorMsg() const noexcept;

    const std::string& getFilename() const noexcept
    {
        return mFilename;
    }

    sqlite3* getHandle() const noexcept
    {
        return mSQLitePtr.get();
    }

    void createFunction(const char* apFuncName,
                        int         aNbArg,
                        bool        abDeterministic,
                        void*       apApp,
                        void      (*apFunc)(sqlite3_context *, int, sqlite3_value **),
                        void      (*apStep)(sqlite3_context *, int, sqlite3_value **) = nullptr,
                        void      (*apFinal)(sqlite3_context *) = nullptr,
                        void      (*apDestroy)(void *) = nullptr);

    void loadExtension(const char* apExtensionName, const char* apEntryPointName);

    void key(const std::string& aKey) const;

    void rekey(const std::string& aNewKey) const;

    static bool isUnencrypted(const std::string& aFilename);

    static Header getHeaderInfo(const std::string& aFilename);

    Header getHeaderInfo() const
    {
        return getHeaderInfo(mFilename);
    }

    enum BackupType { Save, Load };

    void backup(const char* apFilename, BackupType aType);

    void check(const int aRet) const
    {
        if (SQLite::OK != aRet)
        {
            throw SQLite::Exception(getHandle(), aRet);
        }
    }

private:
    std::unique_ptr<sqlite3, Deleter>   mSQLitePtr;
    std::string                         mFilename;
};

}
