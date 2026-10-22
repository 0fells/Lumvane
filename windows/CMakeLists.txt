cmake_minimum_required(VERSION 3.20)

project(Lumvane VERSION 1.0.1)

set(CMAKE_INCLUDE_CURRENT_DIR ON)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
if(MSVC)
    set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    string(REPLACE "/Zi" "/Z7" CMAKE_CXX_FLAGS_RELWITHDEBINFO "${CMAKE_CXX_FLAGS_RELWITHDEBINFO}")
    string(REPLACE "/Zi" "/Z7" CMAKE_C_FLAGS_RELWITHDEBINFO "${CMAKE_C_FLAGS_RELWITHDEBINFO}")
endif()

if (MSVC)
    add_link_options(
            $<$<NOT:$<CONFIG:Debug>>:/INCREMENTAL:NO>
            $<$<NOT:$<CONFIG:Debug>>:/OPT:REF>
            $<$<NOT:$<CONFIG:Debug>>:/OPT:ICF>
    )
else ()
    add_compile_options(-ffunction-sections -fdata-sections)
    if (APPLE)
        add_link_options(-Wl,-dead_strip)
    else ()
        add_link_options(-Wl,--gc-sections)
    endif ()
endif ()

if (UNIX AND NOT APPLE)
    set(QT_DBUS_COMPONENT DBus)
    set(QT_DBUS_LIBRARY Qt6::DBus)
endif ()

find_package(Qt6 REQUIRED COMPONENTS Widgets Network LinguistTools ${QT_DBUS_COMPONENT})

if (APPLE)
    include("cmake/macos/macos.cmake")
elseif (WIN32)
    include("cmake/windows/windows.cmake")
else ()
    include("cmake/linux/linux.cmake")
endif ()

if (NOT MSVC)
    add_compile_options(-fno-omit-frame-pointer)
endif()

add_compile_definitions(QT_MESSAGELOGCONTEXT)

if (MSVC)
    get_filename_component(LUMVANE_REPO_DIR "${CMAKE_SOURCE_DIR}/.." ABSOLUTE)
    file(TO_NATIVE_PATH "${LUMVANE_REPO_DIR}/" LUMVANE_REPO_DIR_NATIVE)
    add_compile_options("/d1trimfile:${LUMVANE_REPO_DIR_NATIVE}" "/d1trimfile:${LUMVANE_REPO_DIR}/")
endif ()

message("[CMAKE_PREFIX_PATH] ${CMAKE_PREFIX_PATH}")

list(APPEND CMAKE_FIND_ROOT_PATH ${CMAKE_PREFIX_PATH})
message("[CMAKE_FIND_ROOT_PATH] ${CMAKE_FIND_ROOT_PATH}")

set(BUILD_SHARED_LIBS OFF)

include("cmake/print.cmake")
include("cmake/lumvane.cmake")
include("cmake/QHotkey.cmake")

find_package(Threads)

if (LUMVANE_PACKAGE)
    lumvane_add_compile_definitions(LUMVANE_USE_APPDATA)
endif ()

include("cmake/myproto.cmake")
list(APPEND LUMVANE_EXTERNAL_TARGETS myproto)

list(APPEND LUMVANE_EXTERNAL_TARGETS qhotkey)

set(PROJECT_SOURCES
        ${PLATFORM_SOURCES}

        src/main.cpp
        src/global/Configs.cpp
        src/global/Utils.cpp
        include/global/Logger.hpp
        src/global/Logger.cpp
        include/global/PeriodicRunner.hpp
        src/global/PeriodicRunner.cpp
        src/global/HTTPRequestHelper.cpp
        src/global/DeviceDetailsHelper.cpp
        src/global/CountryHelper.cpp

        3rdparty/qrcodegen.cpp
        3rdparty/QtExtKeySequenceEdit.cpp
        3rdparty/QrDecoder.cpp

        src/ui/widget/LogHighlighter.cpp
        include/ui/widget/LogHighlighter.hpp
        src/ui/widget/AutoCompleteTextEdit.cpp
        include/ui/widget/AutoCompleteTextEdit.hpp
        src/ui/widget/JsonModel.cpp
        include/ui/widget/JsonModel.hpp
        src/ui/widget/JsonEditor.cpp
        include/ui/widget/JsonEditor.hpp
        include/ui/widget/JsonEditor.ui
        src/ui/widget/SpeedWidget.cpp
        include/ui/widget/SpeedWidget.hpp
        src/sys/SystemProxy.cpp
        include/sys/SystemProxy.hpp

        3rdparty/quirc/decode.c
        3rdparty/quirc/identify.c
        3rdparty/quirc/quirc.c
        3rdparty/quirc/version_db.c

        3rdparty/SQLiteCpp/src/Backup.cpp
        3rdparty/SQLiteCpp/src/Column.cpp
        3rdparty/SQLiteCpp/src/Database.cpp
        3rdparty/SQLiteCpp/src/Exception.cpp
        3rdparty/SQLiteCpp/src/Savepoint.cpp
        3rdparty/SQLiteCpp/src/sqlite3.c
        3rdparty/SQLiteCpp/src/Statement.cpp
        3rdparty/SQLiteCpp/src/Transaction.cpp

        src/api/RPC.cpp

        src/stats/traffic/TrafficLooper.cpp
        include/stats/traffic/TrafficStatsManager.hpp
        src/stats/traffic/TrafficStatsManager.cpp

        include/configs/sub/GroupUpdater.hpp
        src/configs/sub/GroupUpdater.cpp
        include/configs/sub/RouteUpdater.hpp
        src/configs/sub/RouteUpdater.cpp
        src/configs/sub/clash.cpp

        include/sys/Process.hpp
        src/sys/Process.cpp

        include/sys/ProcessMetrics.hpp
        src/sys/ProcessMetrics.cpp

        include/sys/UrlScheme.hpp
        src/sys/UrlScheme.cpp

        include/ui/setting/ThemeManager.hpp
        src/ui/setting/ThemeManager.cpp
        src/ui/setting/Icon.cpp

        src/ui/mainWindow/mainwindow_setup.cpp
        src/ui/mainWindow/mainwindow_events.cpp
        src/ui/mainWindow/mainwindow_groups.cpp
        src/ui/mainWindow/mainwindow_view.cpp
        src/ui/mainWindow/mainwindow_connections.cpp
        src/ui/mainWindow/mainwindow_profiles.cpp
        src/ui/mainWindow/mainwindow_deeplink.cpp
        src/ui/mainWindow/mainwindow_system.cpp
        src/ui/mainWindow/mainwindow_log.cpp
        src/ui/mainWindow/mainwindow_hotkeys.cpp
        src/ui/mainWindow/mainwindow_profile_lifecycle.cpp
        src/ui/mainWindow/mainwindow_autoselector.cpp
        src/ui/mainWindow/TestRunner.cpp
        include/ui/mainWindow/MainWindowInternal.h
        include/ui/mainWindow/TestRunner.h
        include/ui/mainwindow.h
        include/ui/mainwindow.ui
        include/ui/widget/StartStopButton.hpp
        src/ui/widget/StartStopButton.cpp
        include/ui/widget/Sparkline.hpp
        src/ui/widget/Sparkline.cpp
        include/ui/widget/EmptyProfilesView.hpp
        src/ui/widget/EmptyProfilesView.cpp
        include/ui/widget/TitleBar.hpp
        src/ui/widget/TitleBar.cpp
        include/ui/widget/TrayProfileSelector.hpp
        src/ui/widget/TrayProfileSelector.cpp
        include/ui/utils/ProfileTypeDelegate.h
        src/ui/utils/ProfileTypeDelegate.cpp
        include/ui/utils/DataViewHtmlGenerator.h
        src/ui/utils/DataViewHtmlGenerator.cpp
        include/ui/utils/ProfilesTableModel.h
        src/ui/utils/ProfilesTableModel.cpp
        include/ui/utils/ProfilesFilterProxyModel.h
        src/ui/utils/ProfilesFilterProxyModel.cpp
        include/ui/utils/ProfilesTableView.h
        src/ui/utils/ProfilesTableView.cpp
        include/ui/utils/ProfilesTableVerticalHeader.h
        src/ui/utils/ProfilesTableVerticalHeader.cpp
        include/ui/utils/ProfilesTableFilterHeader.h

        include/ui/profile/dialog_edit_profile.h
        src/ui/profile/dialog_edit_profile.cpp
        src/ui/profile/dialog_edit_profile_xhttp.cpp
        include/ui/profile/dialog_edit_profile.ui
        include/ui/group/dialog_edit_group.h
        src/ui/group/dialog_edit_group.cpp
        include/ui/group/dialog_edit_group.ui

        include/ui/profile/edit_chain.h
        src/ui/profile/edit_chain.cpp
        include/ui/profile/edit_chain.ui
        include/ui/profile/edit_autoselector.h
        src/ui/profile/edit_autoselector.cpp
        include/ui/profile/edit_autoselector.ui
        include/ui/profile/edit_shadowsocks.h
        src/ui/profile/edit_shadowsocks.cpp
        include/ui/profile/edit_shadowsocks.ui
        include/ui/profile/edit_vmess.h
        src/ui/profile/edit_vmess.cpp
        include/ui/profile/edit_vmess.ui
        include/ui/profile/edit_vless.h
        src/ui/profile/edit_vless.cpp
        include/ui/profile/edit_vless.ui
        include/ui/profile/edit_anytls.h
        src/ui/profile/edit_anytls.cpp
        include/ui/profile/edit_anytls.ui
        include/ui/profile/edit_mieru.h
        src/ui/profile/edit_mieru.cpp
        include/ui/profile/edit_mieru.ui
        include/ui/profile/edit_tailscale.h
        src/ui/profile/edit_tailscale.cpp
        include/ui/profile/edit_tailscale.ui
        include/ui/profile/edit_trojan.h
        src/ui/profile/edit_trojan.cpp
        include/ui/profile/edit_trojan.ui
        include/ui/profile/edit_http.h
        src/ui/profile/edit_http.cpp
        include/ui/profile/edit_http.ui
        include/ui/profile/edit_socks.h
        src/ui/profile/edit_socks.cpp
        include/ui/profile/edit_socks.ui
        include/ui/profile/edit_hysteria.h
        src/ui/profile/edit_hysteria.cpp
        include/ui/profile/edit_hysteria.ui
        include/ui/profile/edit_tuic.h
        src/ui/profile/edit_tuic.cpp
        include/ui/profile/edit_tuic.ui
        include/ui/profile/edit_juicity.h
        src/ui/profile/edit_juicity.cpp
        include/ui/profile/edit_juicity.ui
        include/ui/profile/edit_trusttunnel.h
        src/ui/profile/edit_trusttunnel.cpp
        include/ui/profile/edit_trusttunnel.ui
        include/ui/profile/edit_shadowtls.h
        src/ui/profile/edit_shadowtls.cpp
        include/ui/profile/edit_shadowtls.ui
        include/ui/profile/edit_naive.h
        src/ui/profile/edit_naive.cpp
        include/ui/profile/edit_naive.ui

        include/ui/profile/edit_custom.h
        src/ui/profile/edit_custom.cpp
        include/ui/profile/edit_custom.ui

        include/ui/profile/edit_extra_core.h
        src/ui/profile/edit_extra_core.cpp
        include/ui/profile/edit_extra_core.ui

        include/ui/profile/edit_wireguard.h
        src/ui/profile/edit_wireguard.cpp
        include/ui/profile/edit_wireguard.ui

        src/ui/setting/dialog_basic_settings.cpp
        include/ui/setting/dialog_basic_settings.h
        include/ui/setting/dialog_basic_settings.ui

        src/ui/group/dialog_manage_groups.cpp
        include/ui/group/dialog_manage_groups.h
        include/ui/group/dialog_manage_groups.ui

        src/ui/setting/dialog_manage_routes.cpp
        include/ui/setting/dialog_manage_routes.h
        include/ui/setting/dialog_manage_routes.ui

        src/ui/setting/dialog_vpn_settings.cpp
        include/ui/setting/dialog_vpn_settings.h
        include/ui/setting/dialog_vpn_settings.ui

        src/ui/stats/dialog_traffic_stats.cpp
        include/ui/stats/dialog_traffic_stats.h
        include/ui/stats/dialog_traffic_stats.ui
        src/ui/stats/TrafficChartWidget.cpp
        include/ui/stats/TrafficChartWidget.h

        src/ui/stats/dialog_runtime_stats.cpp
        include/ui/stats/dialog_runtime_stats.h
        include/ui/stats/dialog_runtime_stats.ui
        src/ui/stats/MiniChartWidget.cpp
        include/ui/stats/MiniChartWidget.h

        src/ui/setting/dialog_hotkey.cpp
        include/ui/setting/dialog_hotkey.h
        include/ui/setting/dialog_hotkey.ui

        src/ui/profile/ProxyItem.cpp
        include/ui/profile/ProxyItem.h
        include/ui/profile/ProxyItem.ui
        src/ui/group/GroupItem.cpp
        include/ui/group/GroupItem.h
        include/ui/group/GroupItem.ui
        src/ui/setting/RouteItem.cpp
        include/ui/setting/RouteItem.h
        include/ui/setting/RouteItem.ui

        src/ui/setting/RawRouteItem.cpp
        include/ui/setting/RawRouteItem.h

        res/Lumvane.qrc
        ${LUMVANE_RC}
        src/ui/profile/edit_ssh.cpp
        include/ui/profile/edit_ssh.h
        include/ui/profile/edit_ssh.ui
        include/ui/mainwindow_interface.h
        include/stats/connections/connectionLister.hpp
        src/stats/connectionLister/connectionLister.cpp
        include/sys/windows/eventHandler.h
        include/configs/baseConfig.h
        include/configs/common/DialFields.h
        include/configs/common/TLS.h
        include/configs/common/multiplex.h
        include/configs/common/transport.h
        include/configs/common/Outbound.h
        include/configs/common/OutboundFactory.h

        src/configs/common/DialFields.cpp
        src/configs/common/TLS.cpp
        src/configs/common/multiplex.cpp
        src/configs/common/transport.cpp
        src/configs/common/Outbound.cpp
        src/configs/common/OutboundFactory.cpp
        include/configs/outbounds/socks.h
        include/configs/outbounds/http.h
        include/configs/outbounds/shadowsocks.h
        include/configs/outbounds/vmess.h
        include/configs/outbounds/trojan.h
        include/configs/outbounds/hysteria.h
        include/configs/outbounds/vless.h
        include/configs/outbounds/tuic.h
        include/configs/outbounds/juicity.h
        include/configs/outbounds/trusttunnel.h
        include/configs/outbounds/naive.h
        include/configs/outbounds/shadowtls.h
        include/configs/outbounds/anyTLS.h
        include/configs/outbounds/mieru.h
        include/configs/outbounds/ssh.h
        
        src/configs/outbounds/anyTLS.cpp
        src/configs/outbounds/mieru.cpp
        src/configs/outbounds/http.cpp
        src/configs/outbounds/hysteria.cpp
        src/configs/outbounds/shadowsocks.cpp
        src/configs/outbounds/socks.cpp
        src/configs/outbounds/ssh.cpp
        src/configs/outbounds/tailscale.cpp
        src/configs/outbounds/trojan.cpp
        src/configs/outbounds/tuic.cpp
        src/configs/outbounds/juicity.cpp
        src/configs/outbounds/trusttunnel.cpp
        src/configs/outbounds/naive.cpp
        src/configs/outbounds/shadowtls.cpp
        src/configs/outbounds/vless.cpp
        src/configs/outbounds/vmess.cpp
        src/configs/outbounds/wireguard.cpp
        src/configs/outbounds/custom.cpp
        include/configs/generate.h
        src/configs/generate.cpp
        include/configs/common/utils.h
        src/configs/common/utils.cpp
        include/configs/outbounds/custom.h
        include/configs/outbounds/chain.h
        include/configs/outbounds/autoselector.h
        src/configs/outbounds/autoselector.cpp
        include/configs/AutoSelectorPlan.h
        src/configs/AutoSelectorPlan.cpp
        include/stats/autoselector/AutoSelectorMonitor.hpp
        src/stats/autoselector/AutoSelectorMonitor.cpp
        include/ui/stats/dialog_auto_selector.h
        src/ui/stats/dialog_auto_selector.cpp
        include/configs/outbounds/extracore.h
        include/ui/profile/edit_advanced.h
        src/ui/profile/edit_advanced.cpp
        include/ui/profile/edit_advanced.ui
        include/configs/common/xrayStreamSetting.h
        include/configs/outbounds/xrayVless.h
        include/configs/common/xrayMultiplex.h
        src/configs/common/xrayMultiplex.cpp
        src/configs/common/xrayStreamSetting.cpp
        src/configs/outbounds/xrayVless.cpp
        include/ui/profile/edit_xrayvless.h
        src/ui/profile/edit_xrayvless.cpp
        include/ui/profile/edit_xrayvless.ui
        include/ui/profile/edit_direct.h
        include/ui/profile/edit_direct.ui
        src/ui/profile/edit_direct.cpp

        include/database/Database.h
        src/database/Database.cpp
        include/database/ProfilesRepo.h
        include/database/GroupsRepo.h
        include/database/RoutesRepo.h
        include/database/SettingsRepo.h
        include/database/TrafficStatsRepo.h

        include/database/entities/Profile.h
        include/database/entities/Group.h
        include/database/entities/RouteProfile.h
        include/database/entities/RouteRule.h

        src/database/entities/Profile.cpp
        src/database/entities/Group.cpp
        src/database/entities/RouteProfile.cpp
        src/database/entities/RouteRule.cpp

        src/database/ProfilesRepo.cpp
        src/database/GroupsRepo.cpp
        src/database/RoutesRepo.cpp
        src/database/TrafficStatsRepo.cpp

        include/database/DatabaseManager.h
        src/database/DatabaseManager.cpp

        include/database/SettingsRepo.h
        src/database/SettingsRepo.cpp
        include/configs/sub/warp.h
        src/configs/sub/warp.cpp
        include/global/Common.h
        src/global/Common.cpp
)

if (NOT APPLE AND Qt6_VERSION VERSION_GREATER_EQUAL 6.9.0)
    list(APPEND PROJECT_SOURCES res/EmojiFont.qrc)
    if (WIN32)
        list(APPEND PROJECT_SOURCES res/EmojiFontWin.qrc)
    endif ()
    set_property(SOURCE res/EmojiFont.qrc res/EmojiFontWin.qrc
            PROPERTY AUTORCC_OPTIONS "--compress-algo;zlib;--compress;9;--threshold;5")
endif ()

qt_add_executable(Lumvane
        MANUAL_FINALIZATION
        ${PROJECT_SOURCES}
)

set_property(TARGET Lumvane PROPERTY AUTOUIC ON)
set_property(TARGET Lumvane PROPERTY AUTOMOC ON)
set_property(TARGET Lumvane PROPERTY AUTORCC ON)


set_target_properties(Lumvane PROPERTIES
        WIN32_EXECUTABLE TRUE
        UNITY_BUILD ON
)
set_property(SOURCE "3rdparty/SQLiteCpp/src/sqlite3.c" PROPERTY SKIP_UNITY_BUILD_INCLUSION ON)

set(LUMVANE_SQLITE_DEFINITIONS
        SQLITE_DQS=0
        SQLITE_DEFAULT_MEMSTATUS=0
        SQLITE_DEFAULT_WAL_SYNCHRONOUS=1
        SQLITE_LIKE_DOESNT_MATCH_BLOBS
        SQLITE_MAX_EXPR_DEPTH=0
        SQLITE_UNTESTABLE
        SQLITE_OMIT_DEPRECATED
        SQLITE_OMIT_SHARED_CACHE
        SQLITE_OMIT_PROGRESS_CALLBACK
        SQLITE_OMIT_AUTHORIZATION
        SQLITE_OMIT_LOAD_EXTENSION
        SQLITE_OMIT_COMPLETE
        SQLITE_OMIT_GET_TABLE
        SQLITE_OMIT_TRACE
        SQLITE_OMIT_UTF16
        SQLITE_OMIT_INCRBLOB
        SQLITE_OMIT_DESERIALIZE
        SQLITE_OMIT_JSON
)
target_compile_definitions(Lumvane PRIVATE ${LUMVANE_SQLITE_DEFINITIONS})

set(TS_FILES
        res/translations/ru_RU.ts
)
set(LUPDATE_OPTIONS
        -locations none -no-obsolete
)
qt_add_lupdate(Lumvane TS_FILES ${TS_FILES} OPTIONS ${LUPDATE_OPTIONS})
qt_add_lrelease(Lumvane TS_FILES ${TS_FILES} QM_FILES_OUTPUT_VARIABLE QM_FILES)

configure_file(res/translations/translations.qrc ${CMAKE_BINARY_DIR} COPYONLY)
target_sources(Lumvane PRIVATE ${CMAKE_BINARY_DIR}/translations.qrc)

target_link_libraries(Lumvane PRIVATE
        Qt6::Widgets Qt6::Network ${QT_DBUS_LIBRARY}
        Threads::Threads
        ${LUMVANE_EXTERNAL_TARGETS}
        ${PLATFORM_LIBRARIES}
)

qt_finalize_executable(Lumvane)

option(LUMVANE_TESTS "Build lumvane_tests" OFF)
if (LUMVANE_TESTS)
    find_package(Qt6 REQUIRED COMPONENTS Test)
    set(LUMVANE_TEST_SOURCES ${PROJECT_SOURCES})
    list(REMOVE_ITEM LUMVANE_TEST_SOURCES src/main.cpp)
    qt_add_executable(lumvane_tests
            ${LUMVANE_TEST_SOURCES}
            tests/tst_lumvane.cpp
    )
    set_target_properties(lumvane_tests PROPERTIES
            AUTOUIC ON
            AUTOMOC ON
            AUTORCC ON
            UNITY_BUILD ON
    )
    target_compile_definitions(lumvane_tests PRIVATE
            ${LUMVANE_SQLITE_DEFINITIONS}
            LUMVANE_TESTDATA_DIR="${CMAKE_SOURCE_DIR}/../testdata"
    )
    target_link_libraries(lumvane_tests PRIVATE
            Qt6::Widgets Qt6::Network Qt6::Test ${QT_DBUS_LIBRARY}
            Threads::Threads
            ${LUMVANE_EXTERNAL_TARGETS}
            ${PLATFORM_LIBRARIES}
    )
    enable_testing()
    add_test(NAME lumvane_tests COMMAND lumvane_tests -platform offscreen)
endif ()
