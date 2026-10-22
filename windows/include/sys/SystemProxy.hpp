#pragma once
#include <QHostAddress>
#include <QObject>
#include <QString>
namespace Lumvane::components::proxy {
    void ClearSystemProxy();
    void SetSystemProxy(int http_port, int socks_port, QString scheme);
}

using namespace Lumvane::components;
using namespace Lumvane::components::proxy;
