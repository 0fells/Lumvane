#include "include/sys/SystemProxy.hpp"

#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <wininet.h>
#include <ras.h>
#include <raserror.h>

#include <string>
#include <vector>

#include <QDebug>

#include "include/global/Configs.hpp"

#define LOG(...) qDebug() << __VA_ARGS__

#define QSTRN(num) QString::number(num)

namespace Lumvane::components::proxy {

#define NO_CONST(expr) const_cast<wchar_t *>(expr)
    bool __QueryProxyOptions() {
        INTERNET_PER_CONN_OPTION_LIST List;
        INTERNET_PER_CONN_OPTION Option[5];
        unsigned long nSize = sizeof(INTERNET_PER_CONN_OPTION_LIST);
        Option[0].dwOption = INTERNET_PER_CONN_AUTOCONFIG_URL;
        Option[1].dwOption = INTERNET_PER_CONN_AUTODISCOVERY_FLAGS;
        Option[2].dwOption = INTERNET_PER_CONN_FLAGS;
        Option[3].dwOption = INTERNET_PER_CONN_PROXY_BYPASS;
        Option[4].dwOption = INTERNET_PER_CONN_PROXY_SERVER;
        List.dwSize = sizeof(INTERNET_PER_CONN_OPTION_LIST);
        List.pszConnection = nullptr;
        List.dwOptionCount = 5;
        List.dwOptionError = 0;
        List.pOptions = Option;

        if (!InternetQueryOption(nullptr, INTERNET_OPTION_PER_CONNECTION_OPTION, &List, &nSize)) {
            LOG("InternetQueryOption failed, GLE=" + QSTRN(GetLastError()));
        }

        LOG("System default proxy info:");

        if (Option[0].Value.pszValue != nullptr) {
            LOG(QString::fromWCharArray(Option[0].Value.pszValue));
        }

        if ((Option[2].Value.dwValue & PROXY_TYPE_AUTO_PROXY_URL) == PROXY_TYPE_AUTO_PROXY_URL) {
            LOG("PROXY_TYPE_AUTO_PROXY_URL");
        }

        if ((Option[2].Value.dwValue & PROXY_TYPE_AUTO_DETECT) == PROXY_TYPE_AUTO_DETECT) {
            LOG("PROXY_TYPE_AUTO_DETECT");
        }

        if ((Option[2].Value.dwValue & PROXY_TYPE_DIRECT) == PROXY_TYPE_DIRECT) {
            LOG("PROXY_TYPE_DIRECT");
        }

        if ((Option[2].Value.dwValue & PROXY_TYPE_PROXY) == PROXY_TYPE_PROXY) {
            LOG("PROXY_TYPE_PROXY");
        }

        if (!InternetQueryOption(nullptr, INTERNET_OPTION_PER_CONNECTION_OPTION, &List, &nSize)) {
            LOG("InternetQueryOption failed,GLE=" + QSTRN(GetLastError()));
        }

        if (Option[4].Value.pszValue != nullptr) {
            LOG(QString::fromStdWString(Option[4].Value.pszValue));
        }

        INTERNET_VERSION_INFO Version;
        nSize = sizeof(INTERNET_VERSION_INFO);
        InternetQueryOption(nullptr, INTERNET_OPTION_VERSION, &Version, &nSize);

        if (Option[0].Value.pszValue != nullptr) {
            GlobalFree(Option[0].Value.pszValue);
        }

        if (Option[3].Value.pszValue != nullptr) {
            GlobalFree(Option[3].Value.pszValue);
        }

        if (Option[4].Value.pszValue != nullptr) {
            GlobalFree(Option[4].Value.pszValue);
        }

        return false;
    }
    bool __SetProxyOptions(LPWSTR proxy_full_addr, bool isPAC) {
        INTERNET_PER_CONN_OPTION_LIST list;
        DWORD dwBufSize = sizeof(list);
        list.dwSize = sizeof(list);
        list.pszConnection = nullptr;

        if (nullptr == proxy_full_addr) {
            LOG("Clearing system proxy");
            list.dwOptionCount = 1;
            list.pOptions = new INTERNET_PER_CONN_OPTION[1];

            if (nullptr == list.pOptions) {
                return false;
            }

            list.pOptions[0].dwOption = INTERNET_PER_CONN_FLAGS;
            list.pOptions[0].Value.dwValue = PROXY_TYPE_DIRECT;
        } else if (isPAC) {
            LOG("Setting system proxy for PAC");
            list.dwOptionCount = 2;
            list.pOptions = new INTERNET_PER_CONN_OPTION[2];

            if (nullptr == list.pOptions) {
                return false;
            }

            list.pOptions[0].dwOption = INTERNET_PER_CONN_FLAGS;
            list.pOptions[0].Value.dwValue = PROXY_TYPE_DIRECT | PROXY_TYPE_AUTO_PROXY_URL;
            list.pOptions[1].dwOption = INTERNET_PER_CONN_AUTOCONFIG_URL;
            list.pOptions[1].Value.pszValue = proxy_full_addr;
        } else {
            LOG("Setting system proxy for Global Proxy");
            list.dwOptionCount = 2;
            list.pOptions = new INTERNET_PER_CONN_OPTION[2];

            if (nullptr == list.pOptions) {
                return false;
            }

            list.pOptions[0].dwOption = INTERNET_PER_CONN_FLAGS;
            list.pOptions[0].Value.dwValue = PROXY_TYPE_DIRECT | PROXY_TYPE_PROXY;
            list.pOptions[1].dwOption = INTERNET_PER_CONN_PROXY_SERVER;
            list.pOptions[1].Value.pszValue = proxy_full_addr;
        }

        if (!InternetSetOption(nullptr, INTERNET_OPTION_PER_CONNECTION_OPTION, &list, dwBufSize)) {
            LOG("InternetSetOption failed for LAN, GLE=" + QSTRN(GetLastError()));
        }

        RASENTRYNAME entry;
        entry.dwSize = sizeof(entry);
        std::vector<RASENTRYNAME> entries;
        DWORD size = sizeof(entry), count;
        LPRASENTRYNAME entryAddr = &entry;
        auto ret = RasEnumEntries(nullptr, nullptr, entryAddr, &size, &count);
        if (ERROR_BUFFER_TOO_SMALL == ret) {
            entries.resize(count);
            entries[0].dwSize = sizeof(RASENTRYNAME);
            entryAddr = entries.data();
            ret = RasEnumEntries(nullptr, nullptr, entryAddr, &size, &count);
        }
        if (ERROR_SUCCESS != ret) {
            LOG("Failed to list entry names");
            return false;
        }

        for (DWORD i = 0; i < count; ++i) {
            list.pszConnection = entryAddr[i].szEntryName;
            if (!InternetSetOption(nullptr, INTERNET_OPTION_PER_CONNECTION_OPTION, &list, dwBufSize)) {
                LOG("InternetSetOption failed for connectoid " + QString::fromWCharArray(list.pszConnection) + ", GLE=" + QSTRN(GetLastError()));
            }
        }

        delete[] list.pOptions;
        InternetSetOption(nullptr, INTERNET_OPTION_SETTINGS_CHANGED, nullptr, 0);
        InternetSetOption(nullptr, INTERNET_OPTION_REFRESH, nullptr, 0);
        return true;
    }

    void SetSystemProxy(int httpPort, int socksPort, QString scheme) {
        Q_UNUSED(httpPort)
        if (socksPort <= 0 || socksPort >= 65536) {
            LOG("System proxy: no port to set");
            return;
        }

        if (scheme == "http") scheme = "http://{ip}:{port}";
        else if (scheme == "socks") scheme = "socks={ip}:{port}";
        scheme = scheme.replace("{ip}", "127.0.0.1").replace("{port}", Int2String(socksPort));
        LOG("Windows proxy string: " + scheme);

        std::wstring proxy = scheme.toStdWString();
        __QueryProxyOptions();
        if (!__SetProxyOptions(proxy.data(), false)) {
            LOG("Failed to set proxy.");
        }
        __QueryProxyOptions();
    }

    void ClearSystemProxy() {
        LOG("Clearing System Proxy");
        if (!__SetProxyOptions(nullptr, false)) {
            LOG("Failed to clear proxy.");
        }
    }
}
