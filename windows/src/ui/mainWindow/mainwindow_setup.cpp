#include "include/ui/mainwindow.h"

#include "include/ui/mainWindow/MainWindowInternal.h"
#include "include/ui/mainWindow/TestRunner.h"

#include <QMenu>

#include "include/configs/sub/GroupUpdater.hpp"
#include "include/configs/sub/RouteUpdater.hpp"
#include "include/global/PeriodicRunner.hpp"
#include "include/global/Logger.hpp"
#include "include/stats/autoselector/AutoSelectorMonitor.hpp"
#include "include/ui/stats/dialog_auto_selector.h"
#include "include/sys/Process.hpp"
#include "include/sys/AutoRun.hpp"
#include "include/sys/UrlScheme.hpp"

#include "include/ui/setting/ThemeManager.hpp"
#include "include/ui/setting/Icon.hpp"
#include "include/ui/stats/dialog_traffic_stats.h"
#include "include/ui/stats/dialog_runtime_stats.h"
#include "include/ui/widget/StartStopButton.hpp"
#include "include/ui/widget/Sparkline.hpp"
#include "include/ui/widget/EmptyProfilesView.hpp"
#include "include/ui/widget/TitleBar.hpp"

#include "include/configs/generate.h"
#include "include/database/GroupsRepo.h"
#include "include/database/ProfilesRepo.h"
#include "include/database/RoutesRepo.h"
#include "include/global/Common.h"

#include "include/ui/utils/ProfilesTableFilterHeader.h"
#include "include/ui/utils/ProfilesTableModel.h"
#include "include/ui/utils/ProfileTypeDelegate.h"

#include "include/ui/group/dialog_edit_group.h"

#ifdef Q_OS_WIN
#include <windows.h>
#undef SetPort
#else
#ifdef Q_OS_LINUX
#include <QDBusInterface>
#include <QDBusReply>
#include <sys/socket.h>
#endif
#ifdef Q_OS_MACOS
#include <sys/socket.h>
#include <sys/un.h>
#endif
#include <unistd.h>
#endif

#include <QUuid>

#include <QClipboard>
#include <QScrollBar>
#include <QDesktopServices>
#include <QTimer>
#include <QMessageBox>
#include <QDir>
#if QT_VERSION >= QT_VERSION_CHECK(6, 5, 0)
#include <QStyleHints>
#endif
#include <QFileDialog>
#include <QJsonArray>
#include <QJsonObject>
#include <QToolButton>
#include <QLineEdit>
#include <QHBoxLayout>
#include <QShortcut>
#include "include/global/DeviceDetailsHelper.hpp"

void UI_InitMainWindow() {
    mainwindow = new MainWindow;
}

namespace {

struct RoutePreset {
    QString country;
    QString name;
    QString defaultOutbound;
    QString ruleOutbound;
    QStringList ruleSets;
    bool privateDirect;
};

QList<RoutePreset> builtinRoutePresets() {
    return {
        {"Russia", "Bypass Russia", "proxy", "direct",
         {"geoip-ru", "geosite-ru", "geosite-category-ru", "geosite-ru-available-only-inside"}, true},
        {"Russia", "Proxy Russia Blocked", "direct", "proxy", {"geoip-ru-blocked", "geosite-ru-blocked"}, false},
        {"China", "Bypass China", "proxy", "direct", {"geoip-cn", "geosite-cn", "geosite-geolocation-cn"}, true},
        {"China", "Proxy China Blocked", "direct", "proxy", {"geosite-gfw"}, false},
        {"Iran", "Bypass Iran", "proxy", "direct", {"geoip-ir", "geosite-ir", "geosite-category-ir"}, true},
    };
}

std::shared_ptr<Configs::RouteProfile> buildRoutePreset(const RoutePreset &preset) {
    QJsonArray rules;
    rules.append(QJsonObject{{"name", "DNS"}, {"action", "hijack-dns"}, {"protocol", "dns"}});
    if (preset.privateDirect) {
        rules.append(QJsonObject{{"name", "Private"}, {"action", "route"}, {"ip_is_private", true}, {"outbound", "direct"}});
    }
    rules.append(QJsonObject{{"name", preset.name}, {"action", "route"}, {"outbound", preset.ruleOutbound},
                             {"rule_set", QJsonArray::fromStringList(preset.ruleSets)}});
    const QJsonObject root{{"kind", "lumvane-route-profile"}, {"v", 1}, {"name", preset.name},
                           {"default_outbound", preset.defaultOutbound}, {"rules", rules}};
    QString fatal, warnings;
    auto profile = Configs::RouteProfile::FromShareInput(QJsonObject2QString(root, true), &fatal, &warnings, nullptr);
    if (!profile) MW_show_log(QObject::tr("Built-in routing profile %1 is invalid: %2").arg(preset.name, fatal));
    return profile;
}

}

bool MainWindow::verify_core_pid(QLocalSocket *socket) {
    if (!core_process) return false;
    qint64 expectedPid = core_process->processId();
    if (expectedPid <= 0) return false;

#if defined(Q_OS_LINUX)
    struct ucred cred = {};
    socklen_t credLen = sizeof(cred);
    if (getsockopt(static_cast<int>(socket->socketDescriptor()), SOL_SOCKET, SO_PEERCRED, &cred, &credLen) == 0) {
        return static_cast<qint64>(cred.pid) == expectedPid;
    }
    return false;
#elif defined(Q_OS_MACOS)
    pid_t pid = 0;
    socklen_t pidLen = sizeof(pid);
    if (getsockopt(static_cast<int>(socket->socketDescriptor()), SOL_LOCAL, LOCAL_PEERPID, &pid, &pidLen) == 0) {
        return static_cast<qint64>(pid) == expectedPid;
    }
    return false;
#elif defined(Q_OS_WIN)
    ULONG pid = 0;
    HANDLE hPipe = reinterpret_cast<HANDLE>(static_cast<qintptr>(socket->socketDescriptor()));
    if (GetNamedPipeClientProcessId(hPipe, &pid)) {
        return static_cast<qint64>(pid) == expectedPid;
    }
    return false;
#else
    Q_UNUSED(socket)
    return true;
#endif
}

static bool themeUsesDarkLog(const QString &) {
    return isDarkPalette();
}

MainWindow::MainWindow(QWidget *parent) : QMainWindow(parent), ui(new Ui::MainWindow) {
    mainwindow = this;
    setAcceptDrops(true);
    MW_dialog_message = [=,this](MwMessage cmd, QStringList args) {
        runOnUiThread([=,this]
        {
            dialog_message_impl(cmd, args);
        });
    };
    MW_handle_deeplink = [=,this](const QString &url) {
        runOnUiThread([=,this]
        {
            handle_deeplink_impl(url);
        });
    };
    MW_import_files = [=,this](const QStringList &paths) {
        runOnUiThread([=,this]
        {
            importFromFiles(paths);
        });
    };

    AutoRun_FixTaskIfNeeded();
    AutoRun_MigrateIfNeeded();

    UrlScheme_RegisterIfNeeded();

    Configs::dataManager->settingsRepo->theme = ThemeManager::Normalize(Configs::dataManager->settingsRepo->theme);
    themeManager->ApplyTheme(Configs::dataManager->settingsRepo->theme);
    ui->setupUi(this);

    setActionsData();
    loadShortcuts();

    last_running_profile_id = Configs::dataManager->settingsRepo->remember_id;

    if (!Configs::dataManager->settingsRepo->mainWindowGeometry.isEmpty()) {
        auto geo = DecodeB64IfValid(Configs::dataManager->settingsRepo->mainWindowGeometry);
        this->restoreGeometry(geo);
    }

    ui->splitter->restoreState(DecodeB64IfValid(Configs::dataManager->settingsRepo->splitter_state));
    setLogHighlighter(themeUsesDarkLog(Configs::dataManager->settingsRepo->theme));
    logDocument->setUndoRedoEnabled(false);
    logDocument->setMaximumBlockCount(Configs::dataManager->settingsRepo->max_log_line);
    ui->masterLogBrowser->setUndoRedoEnabled(false);
    ui->masterLogBrowser->setDocument(logDocument);
    logDocument->setDocumentMargin(10);
    connect(ui->masterLogBrowser->verticalScrollBar(), &QScrollBar::rangeChanged, this, [this](int, int max) {
        if (Configs::dataManager->settingsRepo->log_auto_scroll) ui->masterLogBrowser->verticalScrollBar()->setValue(max);
    });
    applyLogBrowserFont();
    updateLogFilterFields();
    runOnThread([=, this] {
        log_process_loop();
    }, LogThread);

#if QT_VERSION >= QT_VERSION_CHECK(6, 5, 0)
    connect(qApp->styleHints(), &QStyleHints::colorSchemeChanged, this, [=,this](const Qt::ColorScheme& scheme) {
        Q_UNUSED(scheme)
        themeManager->ApplyTheme(Configs::dataManager->settingsRepo->theme);
    });
#endif
    connect(themeManager, &ThemeManager::themeChanged, this, [=,this](const QString& theme){
        QTimer::singleShot(0, this, [=,this] { setLogHighlighter(themeUsesDarkLog(theme)); });
        scheduleProxyListRefresh();
    });
    MW_show_log = [=,this](const QString &log) {
        append_log(log);
        Logging::WriteUserLog(log);
    };

    if (Configs::dataManager->settingsRepo->random_inbound_port)
    {
        Configs::dataManager->settingsRepo->inbound_socks_port = MkPort(Configs::dataManager->settingsRepo->inbound_address);
    }

    runOnNewThread([=, this] {GetDeviceDetails(); });

    auto core_path = QApplication::applicationDirPath() + "/";
    core_path += "LumvaneCore";

    bool coreDebugMode = (Configs::dataManager->settingsRepo->log_level == "debug");

    Configs::dataManager->settingsRepo->core_socket_name =
        "lumvaneIPC-" + QUuid::createUuid().toString(QUuid::WithoutBraces);
    core_server = new QLocalServer(this);
    core_server->setSocketOptions(QLocalServer::UserAccessOption);
    if (!core_server->listen(Configs::dataManager->settingsRepo->core_socket_name)) {
        qWarning() << "Failed to start IPC server:" << core_server->errorString();
        qApp->quit();
    }

    connect(core_server, &QLocalServer::newConnection, this, [=, this]() {
        auto socket = core_server->nextPendingConnection();
        int profileId = -1;
        {
            QMutexLocker lock(&coreProcessMutex);
            if (!verify_core_pid(socket)) {
                MW_show_log("[Warn] IPC connection from unexpected process rejected");
                socket->close();
                socket->deleteLater();
                return;
            }
            if (core_process) {
                profileId = core_process->start_profile_when_core_is_up;
                core_process->start_profile_when_core_is_up = -1;
            }
        }
        setup_rpc(socket);
        Configs::dataManager->settingsRepo->core_running = true;
        LOG_INFO(QString("elevated: %1").arg(Configs::IsAdmin() ? "yes" : "no"));
        MW_dialog_message(MwMessage::CoreStarted, {Int2String(profileId)});
    });

    auto socketFullName = core_server->fullServerName();
    runOnThread(
        [=, this] {
            QMutexLocker lock(&coreProcessMutex);
            core_process = new Configs_sys::CoreProcess(core_path, socketFullName, coreDebugMode);
            const bool relaunchedForMode = Configs::dataManager->settingsRepo->flag_restart_tun_on ||
                                           Configs::dataManager->settingsRepo->flag_dns_set;
            if ((Configs::dataManager->settingsRepo->remember_enable || relaunchedForMode) &&
                Configs::dataManager->settingsRepo->remember_id >= 0) {
                core_process->start_profile_when_core_is_up =
                    Configs::dataManager->settingsRepo->remember_id;
            }
            core_process->Start();
        },
        DS_cores);

    if (!Configs::dataManager->settingsRepo->font.isEmpty()) {
        auto font = qApp->font();
        font.setFamily(Configs::dataManager->settingsRepo->font);
        qApp->setFont(font);
    }
    if (Configs::dataManager->settingsRepo->font_size != 0) {
        auto font = qApp->font();
        font.setPointSize(Configs::dataManager->settingsRepo->font_size);
        qApp->setFont(font);
    }

    parallelCoreCallPool->setMaxThreadCount(10);
    testRunner = std::make_unique<TestRunner>(this);
    ui->menu_start->setShortcuts({QKeySequence(Qt::Key_Return), QKeySequence(Qt::Key_Enter)});
    connect(ui->menu_start, &QAction::triggered, this, [=,this]() { profile_start(); });
    connect(ui->menu_stop, &QAction::triggered, this, [=,this]() { profile_stop(false, false, true); });
    connect(ui->toolButton_startstop, &QAbstractButton::clicked, this, [=,this]() {
        if (running != nullptr) profile_stop(false, false, true);
        else profile_start();
    });
    connect(ui->tabWidget->tabBar(), &QTabBar::tabMoved, this, [=,this](int from, int to) {
        QList<int> tabOrder;
        for (int i = 0; i < ui->tabWidget->tabBar()->count(); i++) {
            tabOrder += ui->tabWidget->tabBar()->tabData(i).toInt();
        }
        Configs::dataManager->groupsRepo->SetGroupsTabOrder(tabOrder);
        on_tabWidget_currentChanged(ui->tabWidget->tabBar()->currentIndex());
    });
    ui->label_running->installEventFilter(this);
    ui->label_inbound->installEventFilter(this);
    ui->splitter->installEventFilter(this);
    ui->aaaaaaaaaaaaaaaaaa->installEventFilter(this);
    ui->tabWidget->installEventFilter(this);
    ui->stats_widget->installEventFilter(this);
    auto *tableCorner = new QWidget(this);
    auto *tableCornerLayout = new QHBoxLayout(tableCorner);
    tableCornerLayout->setContentsMargins(0, 6, 10, 6);
    profileSearch = new QLineEdit(tableCorner);
    profileSearch->setObjectName("profileSearch");
    profileSearch->setPlaceholderText(tr("Search"));
    profileSearch->setAccessibleName(tr("Search"));
    profileSearch->setClearButtonEnabled(true);
    profileSearch->setFixedWidth(220);
    profileSearch->setToolTip(QString("%1\n%2").arg(tr("Name, address, protocol or country"), QKeySequence(QKeySequence::Find).toString(QKeySequence::NativeText)));
    auto *searchIcon = new QAction(profileSearch);
    searchIcon->setObjectName("searchIcon");
    profileSearch->addAction(searchIcon, QLineEdit::LeadingPosition);
    profileSearch->installEventFilter(this);
    connect(profileSearch, &QLineEdit::textChanged, this, [this](const QString &text) {
        if (profilesFilterModel) profilesFilterModel->setSearch(text);
    });
    connect(new QShortcut(QKeySequence::Find, this), &QShortcut::activated, this, [this] {
        profileSearch->setFocus(Qt::ShortcutFocusReason);
        profileSearch->selectAll();
    });
    tableCornerLayout->addWidget(profileSearch);
    ui->tabWidget->setCornerWidget(tableCorner, Qt::TopRightCorner);

    ui->aaaaaaaaaaaa->setContentsMargins(0, 0, 0, 0);
    ui->gridLayout_2->setContentsMargins(0, 0, 0, 0);
    for (auto *layout : {ui->horizontalLayout_3, ui->horizontalLayout_4, ui->horizontalLayout_6}) layout->setContentsMargins(0, 0, 0, 0);
    ui->splitter->setHandleWidth(1);
    ui->splitter->setCollapsible(0, false);
    ui->splitter->setCollapsible(1, false);
    ui->splitter->setStretchFactor(0, 1);
    ui->splitter->setStretchFactor(1, 0);
    panelToggle = new QToolButton(ui->stats_widget);
    panelToggle->setObjectName("panelToggle");
    panelToggle->setAutoRaise(true);
    connect(panelToggle, &QToolButton::clicked, this, &MainWindow::toggleBottomPanel);
    ui->stats_widget->setCornerWidget(panelToggle, Qt::TopRightCorner);
    connect(ui->splitter, &QSplitter::splitterMoved, this, &MainWindow::updatePanelToggle);
    connect(ui->stats_widget->tabBar(), &QTabBar::tabBarClicked, this, [this](int) {
        if (bottomPanelCollapsed()) toggleBottomPanel();
    });
    RegisterHotkey(false);
    auto last_size = Configs::dataManager->settingsRepo->mw_size.split("x");
    if (last_size.length() == 2) {
        auto w = last_size[0].toInt();
        auto h = last_size[1].toInt();
        if (w > 0 && h > 0) {
            resize(w, h);
        }
    }

    software_name = "Lumvane";
    software_core_name = "sing-box";
    if (auto dashDir = QDir("dashboard"); !dashDir.exists() && QDir().mkdir("dashboard")) {
        if (auto dashFile = QFile(":/Lumvane/dashboard-notice.html"); dashFile.exists() && dashFile.open(QIODevice::ReadOnly))
        {
            auto data = dashFile.readAll();
            if (auto dest = QFile("dashboard/index.html"); dest.open(QIODevice::Truncate | QIODevice::WriteOnly))
            {
                dest.write(data);
                dest.close();
            }
            dashFile.close();
        }
    }
    if (auto iconsDir = QDir("icons"); !iconsDir.exists()) {
        QDir().mkdir("icons") ? qDebug("created icons dir") : qDebug("Failed to create icons dir");
    }

    titleBar = new TitleBar(this);
    ui->verticalLayout_3->insertWidget(0, titleBar);

    ui->toolButton_program->setMenu(ui->menu_program);
    ui->toolButton_preferences->setMenu(ui->menu_preferences);
    ui->toolButton_routing->setMenu(ui->menuRouting_Menu);
    ui->toolButton_testing->setMenu(ui->menuTesting);
    ui->toolButton_tools->setMenu(ui->menuTools);

    {
        QFont f = ui->label_status->font();
        f.setPointSizeF(f.pointSizeF() * 1.45);
        f.setWeight(QFont::DemiBold);
        ui->label_status->setFont(f);
    }
    ui->label_status->setTextFormat(Qt::RichText);
    ui->connectionLayout->setContentsMargins(20, 16, 20, 16);
    ui->connectionLayout->setSpacing(18);
    ui->label_speed->setTextFormat(Qt::RichText);
    ui->profilesTableView->setShowGrid(false);
    ui->profilesTableView->verticalHeader()->setVisible(false);
    ui->profilesTableView->horizontalHeader()->setDefaultAlignment(Qt::AlignLeft | Qt::AlignVCenter);
    ui->profilesTableView->setItemDelegate(new ProfileTextDelegate(ui->profilesTableView));
    ui->profilesTableView->setItemDelegateForColumn(ProfilesTableModel::ColType, new ProfileTypeDelegate(ui->profilesTableView));
    ui->modeSegment->setSizePolicy(QSizePolicy::Fixed, QSizePolicy::Fixed);
    ui->modesLayout->insertStretch(0);
    ui->modesLayout->addStretch();
    speedSparkline = new Sparkline(ui->connectionPanel);
    speedSparkline->setMinimumWidth(160);
    speedSparkline->setMaximumWidth(480);
    ui->speedLayout->addWidget(speedSparkline);
    ui->connectionLayout->setStretchFactor(ui->statusLayout, 3);
    ui->connectionLayout->setStretchFactor(ui->speedLayout, 1);
    ui->label_versions->setText(QString("sing-box %1 · Xray %2").arg("…", "…"));

    emptyProfilesView = new EmptyProfilesView(ui->profilesTableView->viewport());
    emptyProfilesView->hide();
    ui->profilesTableView->viewport()->installEventFilter(this);
    connect(emptyProfilesView, &EmptyProfilesView::pasteRequested, ui->menu_add_from_clipboard, &QAction::trigger);
    connect(emptyProfilesView, &EmptyProfilesView::subscriptionRequested, ui->actionAdd_New_Group, &QAction::trigger);
    connect(emptyProfilesView, &EmptyProfilesView::scanQrRequested, ui->menu_scan_qr, &QAction::trigger);
    noMatchesLabel = new QLabel(tr("Nothing found"), ui->profilesTableView->viewport());
    noMatchesLabel->setObjectName("noMatchesLabel");
    noMatchesLabel->setAlignment(Qt::AlignHCenter | Qt::AlignTop);
    noMatchesLabel->setAttribute(Qt::WA_TransparentForMouseEvents);
    noMatchesLabel->hide();

    applyMainWindowChrome();
    connect(themeManager, &ThemeManager::themeChanged, this, [=,this] { applyMainWindowChrome(); });

    if (Configs::dataManager->settingsRepo->splitter_state.isEmpty()) {
        QTimer::singleShot(0, this, [=,this] {
            const int h = ui->splitter->height();
            ui->splitter->setSizes({h * 3 / 4, h / 4});
        });
    }

    designMinimumSize = minimumSize();
    applyTopBarMetrics();

    ui->menubar->setVisible(false);
    connect(ui->actionRuntime_Stats, &QAction::triggered, this, [=, this]() {
        USE_DIALOG(DialogRuntimeStats)
    });
    ui->actionTraffic_Stats->setVisible(!Configs::dataManager->settingsRepo->disable_traffic_aggregation);
    connect(ui->actionTraffic_Stats, &QAction::triggered, this, [=, this]() {
        USE_DIALOG(DialogTrafficStats)
    });
    ui->actionAuto_Selector->setVisible(false);
    connect(ui->actionAuto_Selector, &QAction::triggered, this, [=,this]() {
        if (m_autoSelectorDialog == nullptr) {
            m_autoSelectorDialog = new DialogAutoSelector(this);
            connect(m_autoSelectorDialog, &QDialog::finished, this, [this] {
                m_autoSelectorDialog->deleteLater();
                m_autoSelectorDialog = nullptr;
            });
        }
        m_autoSelectorDialog->refresh();
        m_autoSelectorDialog->show();
        m_autoSelectorDialog->raise();
        m_autoSelectorDialog->activateWindow();
    });

    setupConnectionList();
    ui->stats_widget->tabBar()->setCurrentIndex(Configs::dataManager->settingsRepo->stats_tab);
    connect(ui->stats_widget->tabBar(), &QTabBar::currentChanged, this, [=,this](int index)
    {
        Configs::dataManager->settingsRepo->stats_tab = ui->stats_widget->tabBar()->currentIndex();
        syncConnectionViewState();
    });
    syncConnectionViewState();
    connect(ui->connections->horizontalHeader(), &QHeaderView::sectionClicked, this, [=,this](int index)
    {
            Stats::ConnectionSort sortType;

            switch (index)
            {
            case 1: sortType = Stats::ByProcess; break;
            case 2: sortType = Stats::ByProtocol; break;
            case 3: sortType = Stats::ByOutbound; break;
            case 4: sortType = Stats::ByTraffic; break;
            case 5: sortType = Stats::BySpeed; break;
            default: sortType = Stats::Default; break;
            }

            Stats::connection_lister->setSort(sortType);
            Stats::connection_lister->ForceUpdate();
    });

    speedChartWidget = new SpeedWidget(this);
    ui->graph_tab->layout()->addWidget(speedChartWidget);

    profilesTableModel = new ProfilesTableModel(this);
    profilesFilterModel = new ProfilesFilterProxyModel(this);
    profilesFilterModel->setSourceModel(profilesTableModel);
    ui->profilesTableView->setModel(profilesFilterModel);
    const auto nameBeforeAddress = [this] {
        auto *header = ui->profilesTableView->horizontalHeader();
        if (header->visualIndex(ProfilesTableModel::ColName) > header->visualIndex(ProfilesTableModel::ColAddress)) {
            header->moveSection(header->visualIndex(ProfilesTableModel::ColName), header->visualIndex(ProfilesTableModel::ColAddress));
        }
    };
    nameBeforeAddress();
    connect(profilesFilterModel, &QAbstractItemModel::modelReset, this, nameBeforeAddress);
    connect(profilesTableModel, &QAbstractItemModel::modelReset, this, &MainWindow::updateEmptyProfilesView);
    connect(profilesTableModel, &QAbstractItemModel::rowsInserted, this, &MainWindow::updateEmptyProfilesView);
    connect(profilesTableModel, &QAbstractItemModel::rowsRemoved, this, &MainWindow::updateEmptyProfilesView);
    connect(profilesTableModel, &QAbstractItemModel::layoutChanged, this, &MainWindow::updateEmptyProfilesView);
    connect(profilesFilterModel, &QAbstractItemModel::modelReset, this, &MainWindow::updateEmptyProfilesView);
    connect(profilesFilterModel, &QAbstractItemModel::layoutChanged, this, &MainWindow::updateEmptyProfilesView);
    connect(profilesFilterModel, &QAbstractItemModel::rowsInserted, this, &MainWindow::updateEmptyProfilesView);
    connect(profilesFilterModel, &QAbstractItemModel::rowsRemoved, this, &MainWindow::updateEmptyProfilesView);
    connect(ui->profilesTableView->selectionModel(), &QItemSelectionModel::selectionChanged, this,
            [this] { refresh_startstop_button(); });
    ui->profilesTableView->rowsSwapped = [this](int row1, int row2)
    {
        if (profilesFilterModel->hasActiveFilter()) return;
        if (row1 == row2) return;
        auto group = Configs::dataManager->groupsRepo->CurrentGroup();
        if (group == nullptr) return;
        group->EmplaceProfile(row1, row2);
        profilesTableModel->emplaceProfiles(row1, row2);
        Configs::dataManager->groupsRepo->Save(group);
    };
    connect(ui->profilesTableView->horizontalHeader(), &QHeaderView::sectionClicked, this, [=, this](int logicalIndex) {
        GroupSortAction action;
        if (proxy_last_order == logicalIndex) {
            action.descending = true;
            proxy_last_order = -1;
        } else {
            proxy_last_order = logicalIndex;
        }
        if (logicalIndex == ProfilesTableModel::ColType) {
            auto group = Configs::dataManager->groupsRepo->CurrentGroup();
            action.method = (Configs::dataManager->settingsRepo->show_config_security && group
                             && group->type_sort_by == Configs::typeBy::bySecurity)
                                ? GroupSortMethod::BySecurity
                                : GroupSortMethod::ByType;
        } else if (logicalIndex == ProfilesTableModel::ColAddress) {
            action.method = GroupSortMethod::ByAddress;
        } else if (logicalIndex == ProfilesTableModel::ColName) {
            action.method = GroupSortMethod::ByName;
        } else if (logicalIndex == ProfilesTableModel::ColTestResult) {
            action.method = GroupSortMethod::ByTestResult;
        } else if (logicalIndex == ProfilesTableModel::ColTraffic) {
            action.method = GroupSortMethod::ByTraffic;
        } else {
            return;
        }
        runOnNewThread([=, this] {
            auto currGroup = Configs::dataManager->groupsRepo->CurrentGroup();
            if (currGroup == nullptr) return;
            if (!currGroup->SortProfiles(action)) {
                runOnUiThread([=] {
                    MessageBoxWarning(tr("Action already in progress"), tr("Sorting is already in progress."));
                });
                return;
            }
            Configs::dataManager->groupsRepo->Save(currGroup);
            runOnUiThread([=, this] {
                refresh_proxy_list({}, true);
            });
        });
    });
    connect(ui->profilesTableView->horizontalHeader(), &QHeaderView::sectionResized, this, [=, this](int, int, int) {
        auto group = Configs::dataManager->groupsRepo->CurrentGroup();
        if (Configs::dataManager->settingsRepo->refreshing_group || group == nullptr) return;
        group->column_width.clear();
        for (int i = 0; i < ui->profilesTableView->horizontalHeader()->count(); i++) {
            group->column_width.push_back(ui->profilesTableView->horizontalHeader()->sectionSize(i));
        }
        Configs::dataManager->groupsRepo->Save(Configs::dataManager->groupsRepo->CurrentGroup());
    });
    ui->profilesTableView->horizontalHeader()->setContextMenuPolicy(Qt::CustomContextMenu);
    connect(ui->profilesTableView->horizontalHeader(), &QWidget::customContextMenuRequested, this, [this](const QPoint& pos) {
        auto* header = ui->profilesTableView->horizontalHeader();
        int columnIndex = header->logicalIndexAt(pos);
        auto group = Configs::dataManager->groupsRepo->CurrentGroup();
        if (group == nullptr) return;
        if (columnIndex == ProfilesTableModel::ColType) {
            if (!Configs::dataManager->settingsRepo->show_config_security) return;
            QMenu menu(this);
            auto* sortByLabel = menu.addAction(tr("Sort By:"));
            sortByLabel->setEnabled(false);

            struct TypeSortOption { Configs::typeBy value; QString label; };
            const QList<TypeSortOption> options = {
                { Configs::typeBy::byType, tr("Type") },
                { Configs::typeBy::bySecurity, tr("Security") },
            };
            for (const auto& opt : options) {
                auto* act = menu.addAction(opt.label);
                act->setData(static_cast<int>(opt.value));
                act->setCheckable(true);
                act->setChecked(group->type_sort_by == opt.value);
            }

            auto* chosen = menu.exec(header->mapToGlobal(pos));
            if (chosen == nullptr || !chosen->data().isValid()) return;

            group->type_sort_by = static_cast<Configs::typeBy>(chosen->data().toInt());
            Configs::dataManager->groupsRepo->Save(group);
            GroupSortAction action;
            action.method = group->type_sort_by == Configs::typeBy::bySecurity
                                ? GroupSortMethod::BySecurity
                                : GroupSortMethod::ByType;
            runOnNewThread([=, this] {
                auto currGroup = Configs::dataManager->groupsRepo->CurrentGroup();
                if (currGroup == nullptr) return;
                if (!currGroup->SortProfiles(action)) {
                    runOnUiThread([=] {
                        MessageBoxWarning(tr("Action already in progress"), tr("Sorting is already in progress."));
                    });
                    return;
                }
                Configs::dataManager->groupsRepo->Save(currGroup);
                runOnUiThread([=, this] {
                    refresh_proxy_list({}, true);
                });
            });
            return;
        }
        if (columnIndex == ProfilesTableModel::ColTestResult) {
            QMenu menu(this);
            auto* includeLabel = menu.addAction(tr("Include:"));
            includeLabel->setEnabled(false);

            auto* actionShowOutIP = menu.addAction(tr("Out IP"));
            actionShowOutIP->setCheckable(true);
            actionShowOutIP->setChecked(group->test_items_to_show == Configs::testShowItems::all ||
                group->test_items_to_show == Configs::testShowItems::ipOnly);

            auto* actionShowSpeed = menu.addAction(tr("Speed"));
            actionShowSpeed->setCheckable(true);
            actionShowSpeed->setChecked(group->test_items_to_show == Configs::testShowItems::all ||
                group->test_items_to_show == Configs::testShowItems::speedOnly);

            auto updateTestItemsToShow = [this, group, actionShowOutIP, actionShowSpeed] {
                    const bool ip = actionShowOutIP->isChecked();
                    const bool speed = actionShowSpeed->isChecked();
                    if (ip && speed) group->test_items_to_show = Configs::testShowItems::all;
                    else if (ip) group->test_items_to_show = Configs::testShowItems::ipOnly;
                    else if (speed) group->test_items_to_show = Configs::testShowItems::speedOnly;
                    else group->test_items_to_show = Configs::testShowItems::none;
                    Configs::dataManager->groupsRepo->Save(group);
                    if (group->calculated_column_width.size() > ProfilesTableModel::ColTestResult) {
                        group->calculated_column_width[ProfilesTableModel::ColTestResult] = 0;
                    }
                    refresh_proxy_list();
                };

            connect(actionShowOutIP, &QAction::triggered, this, updateTestItemsToShow);
            connect(actionShowSpeed, &QAction::triggered, this, updateTestItemsToShow);

            menu.addSeparator();
            auto* sortByLabel = menu.addAction(tr("Sort By:"));
            sortByLabel->setEnabled(false);

            struct SortOption { int value; QString label; };
            QList<SortOption> options = {
                { static_cast<int>(Configs::testBy::latency), tr("Latency") },
                { static_cast<int>(Configs::testBy::dlSpeed), tr("Download Speed") },
                { static_cast<int>(Configs::testBy::ulSpeed), tr("Upload Speed") },
                { static_cast<int>(Configs::testBy::ipOut), tr("IP Out") }
            };
            for (const auto& opt : options) {
                auto* act = menu.addAction(opt.label);
                act->setData(opt.value);
                act->setCheckable(true);
                act->setChecked(static_cast<int>(group->test_sort_by) == opt.value);
            }

            auto* chosen = menu.exec(header->mapToGlobal(pos));
            if (chosen == nullptr || !chosen->data().isValid()) return;

            int testSortBy = chosen->data().toInt();
            group->test_sort_by = static_cast<Configs::testBy>(testSortBy);
            Configs::dataManager->groupsRepo->Save(group);
            GroupSortAction action;
            action.method = GroupSortMethod::ByTestResult;
            action.descending = true;
            runOnNewThread([=, this] {
                auto currGroup = Configs::dataManager->groupsRepo->CurrentGroup();
                if (currGroup == nullptr) return;
                if (!currGroup->SortProfiles(action)) {
                    runOnUiThread([=] {
                        MessageBoxWarning(tr("Action already in progress"), tr("Sorting is already in progress."));
                        });
                    return;
                }
                Configs::dataManager->groupsRepo->Save(currGroup);
                runOnUiThread([=, this] {
                    refresh_proxy_list({}, true);
                    });
                });
            return;
        }
        if (columnIndex == ProfilesTableModel::ColTraffic) {
            QMenu menu(this);
            auto* sortByLabel = menu.addAction(tr("Sort By:"));
            sortByLabel->setEnabled(false);

            struct TrafficSortOption { int value; QString label; };
            QList<TrafficSortOption> options = {
                { 0, tr("Total") },
                { 1, tr("Downloaded") },
                { 2, tr("Uploaded") }
            };

            for (const auto& opt : options) {
                auto* act = menu.addAction(opt.label);
                act->setData(opt.value);
                act->setCheckable(true);
                act->setChecked(static_cast<int>(group->traffic_sort_by) == opt.value);
            }

            auto* chosen = menu.exec(header->mapToGlobal(pos));
            if (chosen == nullptr || !chosen->data().isValid()) return;

            int trafficSortBy = chosen->data().toInt();
            group->traffic_sort_by = static_cast<Configs::trafficBy>(trafficSortBy);
            Configs::dataManager->groupsRepo->Save(group);
            GroupSortAction action;
            action.method = GroupSortMethod::ByTraffic;
            action.descending = false;
            runOnNewThread([=, this] {
                auto currGroup = Configs::dataManager->groupsRepo->CurrentGroup();
                if (currGroup == nullptr) return;
                if (!currGroup->SortProfiles(action)) {
                    runOnUiThread([=] {
                        MessageBoxWarning(tr("Action already in progress"), tr("Sorting is already in progress."));
                        });
                    return;
                }
                Configs::dataManager->groupsRepo->Save(Configs::dataManager->groupsRepo->CurrentGroup());
                runOnUiThread([=, this] {
                    refresh_proxy_list();
                    });
                });
            return;
        }
    });
    ui->profilesTableView->verticalHeader()->setStretchLastSection(false);
    ui->profilesTableView->verticalHeader()->setDefaultSectionSize(34);
    ui->profilesTableView->verticalHeader()->setSectionResizeMode(QHeaderView::Fixed);
    ui->profilesTableView->setTabKeyNavigation(false);
    ui->profilesTableView->horizontalHeader()->setResizeContentsPrecision(0);

    connect(ui->profilesTableView->verticalScrollBar(), &QScrollBar::valueChanged, ui->profilesTableView, [=, this] {
        if (!ui->profilesTableView->isVisible()) return;
        refresh_proxy_list_column_size();
    });

    auto *filterHeader = static_cast<ProfilesTableFilterHeader*>(ui->profilesTableView->horizontalHeader());
    filterHeader->setLastFilterColumn(Configs::dataManager->settingsRepo->last_filter_column);
    connect(filterHeader, &ProfilesTableFilterHeader::lastFilterColumnChanged, this, [](int column)
    {
        Configs::dataManager->settingsRepo->last_filter_column = column;
        Configs::dataManager->settingsRepo->Save();
    });

    m_filterRefreshDebounce = new QTimer(this);
    m_filterRefreshDebounce->setSingleShot(true);
    m_filterRefreshDebounce->setInterval(50);
    connect(m_filterRefreshDebounce, &QTimer::timeout, this, [this] { applyProfileFilters(); });

    connect(filterHeader, &ProfilesTableFilterHeader::typeFilterChanged, this, [this](const QString& currentText)
    {
       typeFilterString = currentText;
       m_filterRefreshDebounce->start();
    });
    connect(filterHeader, &ProfilesTableFilterHeader::addressFilterChanged, this, [this](const QString& currentText)
    {
       addressFilterString = currentText;
       m_filterRefreshDebounce->start();
    });
    connect(filterHeader, &ProfilesTableFilterHeader::nameFilterChanged, this, [this](const QString& currentText)
    {
       nameFilterString = currentText;
       m_filterRefreshDebounce->start();
    });
    connect(filterHeader, &ProfilesTableFilterHeader::testFilterChanged, this, [this](const QString& currentText)
    {
       countryFilterString = currentText;
       m_filterRefreshDebounce->start();
    });
    connect(filterHeader, &ProfilesTableFilterHeader::focusTableRequested, this,
            [this](bool selectFirst) { focusProfilesTable(selectFirst); });

    this->refresh_groups();

    tray = new QSystemTrayIcon(nullptr);
    tray->setIcon(GetTrayIcon(Icon::NONE));
    QApplication::setWindowIcon(Icon::Vane());
    setWindowIcon(Icon::App());
    Icon::UseVaneForOtherWindows(this);
    trayMenu = new QMenu();
    trayMenu->addAction(ui->actionShow_window);
    trayStartStop = new QAction(tr("Connect"), trayMenu);
    connect(trayStartStop, &QAction::triggered, this, [=,this]() {
        if (running != nullptr) profile_stop(false, false, true);
        else profile_start();
    });
    trayMenu->addAction(trayStartStop);
    trayMenu->addSeparator();
    trayMenu->addAction(ui->actionStart_with_system);
    trayMenu->addAction(ui->actionRemember_last_proxy);
    trayMenu->addAction(ui->actionAllow_LAN);
    trayMenu->addSeparator();

    auto *actSelectServer = new QAction(tr("Select Profile"), trayMenu);
    connect(actSelectServer, &QAction::triggered, this, [this]() { openTraySelector(false); });
    trayMenu->addAction(actSelectServer);
    auto *actSelectRouting = new QAction(tr("Select Routing"), trayMenu);
    connect(actSelectRouting, &QAction::triggered, this, [this]() { openTraySelector(true); });
    trayMenu->addAction(actSelectRouting);
    trayMenu->addMenu(ui->menu_spmode);
    trayMenu->addSeparator();

    trayMenu->addAction(ui->actionRestart_Proxy);
    trayMenu->addAction(ui->actionRestart_Program);
    trayMenu->addAction(ui->menu_exit);
    tray->setVisible(!Configs::dataManager->settingsRepo->disable_tray);
    tray->setContextMenu(trayMenu);
    connect(tray, &QSystemTrayIcon::messageClicked, this, [=,this]() { ActivateWindow(this); });
    MW_notify = [=,this](const QString &title, const QString &text) {
        runOnUiThread([=,this] {
            if (tray == nullptr || !tray->isVisible()) return;
            tray->showMessage(title, text, QSystemTrayIcon::Warning, 8000);
        });
    };
    connect(tray, &QSystemTrayIcon::activated, qApp, [=, this](QSystemTrayIcon::ActivationReason reason) {
        if (reason == QSystemTrayIcon::Trigger) {
            trayClickEvent();
        }
    });

    ui->actionRemember_last_proxy->setChecked(Configs::dataManager->settingsRepo->remember_enable);
    ui->actionStart_with_system->setChecked(AutoRun_IsEnabled());
    ui->actionAllow_LAN->setChecked(QStringList{"::", "0.0.0.0"}.contains(Configs::dataManager->settingsRepo->inbound_address));

    connect(ui->actionHide_window, &QAction::triggered, this, [=, this](){ HideWindow(this); });
    connect(ui->menu_open_config_folder, &QAction::triggered, this, [=,this] { QDesktopServices::openUrl(QUrl::fromLocalFile(QDir::currentPath())); });
    connect(ui->actionRestart_Proxy, &QAction::triggered, this, [=,this] {
        runOnThread([=, this] {
            profile_stop(true, true, true);
            core_process->Kill();
        }, DS_cores);
    });
    connect(ui->actionRestart_Program, &QAction::triggered, this, [=,this] { MW_dialog_message(MwMessage::RestartProgram, {}); });
    connect(ui->actionShow_window, &QAction::triggered, this, [=,this] { ActivateWindow(this); });
    connect(ui->actionRemember_last_proxy, &QAction::triggered, this, [=,this](bool checked) {
        Configs::dataManager->settingsRepo->remember_enable = checked;
        ui->actionRemember_last_proxy->setChecked(checked);
        Configs::dataManager->settingsRepo->Save();
    });
    connect(ui->actionStart_with_system, &QAction::triggered, this, [=,this](bool checked) {
        AutoRun_SetEnabled(checked);
        ui->actionStart_with_system->setChecked(checked);
    });
    connect(ui->actionAllow_LAN, &QAction::triggered, this, [=,this](bool checked) {
        Configs::dataManager->settingsRepo->inbound_address = checked ? "::" : "127.0.0.1";
        ui->actionAllow_LAN->setChecked(checked);
        MW_dialog_message(MwMessage::UpdateSettings, {});
    });
    connect(ui->checkBox_VPN, &QAbstractButton::clicked, this, [=,this](bool checked) { set_spmode_vpn(checked); });
    connect(ui->checkBox_SystemProxy, &QAbstractButton::clicked, this, [=,this](bool checked) { set_spmode_system_proxy(checked); });
    connect(ui->menu_spmode, &QMenu::aboutToShow, this, [=,this]() {
        ui->menu_spmode_disabled->setChecked(!(Configs::dataManager->settingsRepo->spmode_system_proxy || Configs::dataManager->settingsRepo->spmode_vpn));
        ui->menu_spmode_system_proxy->setChecked(Configs::dataManager->settingsRepo->spmode_system_proxy);
        ui->menu_spmode_vpn->setChecked(Configs::dataManager->settingsRepo->spmode_vpn);
    });
    connect(ui->menu_spmode_system_proxy, &QAction::triggered, this, [=,this](bool checked) { set_spmode_system_proxy(checked); });
    connect(ui->menu_spmode_vpn, &QAction::triggered, this, [=,this](bool checked) { set_spmode_vpn(checked); });
    connect(ui->menu_spmode_disabled, &QAction::triggered, this, [=,this]() {
        set_spmode_system_proxy(false);
        set_spmode_vpn(false);
    });
    connect(ui->menu_qr, &QAction::triggered, this, [=,this]() { display_qr_link(false); });
    connect(ui->system_dns, &QCheckBox::clicked, this, [=,this](bool checked) {
        if (const auto ok = set_system_dns(checked); !ok) {
            ui->system_dns->setChecked(!checked);
        } else {
            refresh_status();
        }
    });
    if (Configs::dataManager->settingsRepo->show_system_dns) ui->system_dns->show();
    else ui->system_dns->hide();

    connect(ui->menu_server, &QMenu::aboutToShow, this, [=,this](){
        if (auto selected = get_now_selected_list(); selected.empty())
        {
            ui->actionSpeedtest_Selected->setEnabled(false);
            ui->actionUrl_Test_Selected->setEnabled(false);
            ui->menu_resolve_selected->setEnabled(false);
            ui->actionResolve_Selected_Out_IP->setEnabled(false);
        } else
        {
            ui->actionSpeedtest_Selected->setEnabled(true);
            ui->actionUrl_Test_Selected->setEnabled(true);
            ui->menu_resolve_selected->setEnabled(true);
            ui->actionResolve_Selected_Out_IP->setEnabled(true);
        }
        if (testRunner->isRunning()) {
            ui->menu_server->addAction(ui->menu_stop_testing);
        } else {
            ui->menu_server->removeAction(ui->menu_stop_testing);
        }
    });

    connect(ui->menuTesting, &QMenu::aboutToShow, this, [=,this](){
        ui->actionDelete_Group->setEnabled(Configs::dataManager->groupsRepo->GetAllGroupIds().size() > 1);
        if (testRunner->isRunning()) {
            ui->menuTesting->addAction(ui->menu_stop_testing);
        } else {
            ui->menuTesting->removeAction(ui->menu_stop_testing);
        }
    });

    connect(ui->menuTools, &QMenu::aboutToShow, this, [=,this](){
        ui->actionSpeedtest_Current->setEnabled(running != nullptr);
    });

    connect(ui->actionAdd_New_Group, &QAction::triggered, this, [=,this]{
        auto ent = Configs::dataManager->groupsRepo->NewGroup();
        auto dialog = new DialogEditGroup(ent, this);
        int ret = dialog->exec();
        dialog->deleteLater();

        if (ret == QDialog::Accepted) {
            Configs::dataManager->groupsRepo->AddGroup(ent);
            MW_dialog_message(MwMessage::GroupsChanged, {});
        }
    });

    connect(ui->actionEdit_Group, &QAction::triggered, this, [=,this]{
        auto ent = Configs::dataManager->groupsRepo->CurrentGroup();
        if (ent == nullptr) return;
        auto dialog = new DialogEditGroup(ent, this);
        connect(dialog, &QDialog::finished, this, [=,this] {
            if (dialog->result() == QDialog::Accepted) {
                Configs::dataManager->groupsRepo->Save(ent);
                MW_dialog_message(MwMessage::GroupsChanged, {});
            }
            dialog->deleteLater();
        });
        dialog->show();
    });

    connect(ui->actionDelete_Group, &QAction::triggered, this, [=,this]{
        if (Configs::dataManager->groupsRepo->GetAllGroupIds().size() <= 1) return;
        const auto currentGroup = Configs::dataManager->groupsRepo->CurrentGroup();
        if (currentGroup == nullptr) return;
        auto id = currentGroup->id;
        if (QMessageBox::question(this, tr("Confirmation"), tr("Remove %1?").arg(currentGroup->name)) ==
            QMessageBox::StandardButton::Yes) {
            if (running != nullptr) {
                if (running->gid == id) profile_stop(false, true, false);
            }
            Configs::dataManager->groupsRepo->DeleteGroup(id);
            MW_dialog_message(MwMessage::GroupsChanged, {});
        }
    });

    connect(ui->actionUpdate_All_Subscriptions, &QAction::triggered, this, [=,this]{
        if (QMessageBox::question(this, tr("Confirmation"), tr("Update all subscriptions?")) == QMessageBox::StandardButton::Yes) {
            UI_update_all_groups();
        }
    });

    connect(ui->actionRefresh_Column_Widths, &QAction::triggered, this, [=, this] {
        auto ent = Configs::dataManager->groupsRepo->CurrentGroup();
        if (ent == nullptr) return;
        ent->column_width.clear();
        Configs::dataManager->groupsRepo->Save(ent);
        show_group(ent->id);
    });

    connect(ui->menuRouting_Menu, &QMenu::aboutToShow, this, [=,this]()
    {
        ui->menuRouting_Menu->clear();
        ui->menuRouting_Menu->addAction(ui->menu_routing_settings);

        auto* actionAdblock = new QAction(ui->menuRouting_Menu);
        actionAdblock->setText(tr("Enable AdBlock"));
        actionAdblock->setCheckable(true);
        actionAdblock->setChecked(Configs::dataManager->settingsRepo->adblock_enable);
        connect(actionAdblock, &QAction::triggered, this, [=,this](bool checked) {
            Configs::dataManager->settingsRepo->adblock_enable = checked;
            actionAdblock->setChecked(checked);
            Configs::dataManager->settingsRepo->Save();
            if (Configs::dataManager->settingsRepo->started_id >= 0) profile_start(Configs::dataManager->settingsRepo->started_id);
        });
        ui->menuRouting_Menu->addAction(actionAdblock);

        auto* actionWarp = new QAction(ui->menuRouting_Menu);
        actionWarp->setText(tr("Enable Warp"));
        actionWarp->setCheckable(true);
        actionWarp->setChecked(Configs::dataManager->settingsRepo->enable_warp);
        connect(actionWarp, &QAction::triggered, this, [=,this](bool checked) {
            Configs::dataManager->settingsRepo->enable_warp = checked;
            actionWarp->setChecked(checked);
            Configs::dataManager->settingsRepo->Save();
            if (Configs::dataManager->settingsRepo->started_id >= 0) profile_start(Configs::dataManager->settingsRepo->started_id);
        });
        ui->menuRouting_Menu->addAction(actionWarp);

        QMenu* profilesMenu = ui->menuRouting_Menu->addMenu(QObject::tr("Add Built-in Profile"));
        QMap<QString, QMenu*> countryMenus;
        const auto allRoutes = Configs::dataManager->routesRepo->GetAllRouteProfiles();
        QSet<QString> routeNames;
        for (const auto& route : allRoutes) routeNames.insert(route->name);
        for (const auto &preset : builtinRoutePresets())
        {
            if (!countryMenus.contains(preset.country)) countryMenus[preset.country] = profilesMenu->addMenu(preset.country);
            auto* action = new QAction(countryMenus[preset.country]);
            if (routeNames.contains(preset.name)) {
                action->setText(tr("%1 (added)").arg(preset.name));
                action->setEnabled(false);
                countryMenus[preset.country]->addAction(action);
                continue;
            }
            action->setText(preset.name);
            connect(action, &QAction::triggered, this, [=,this]()
            {
                auto profile = buildRoutePreset(preset);
                if (!profile) return;
                Configs::dataManager->routesRepo->AddRouteProfile(profile);
                MW_show_log(QObject::tr("Added routing profile: %1").arg(preset.name));
            });
            countryMenus[preset.country]->addAction(action);
        }

        ui->menuRouting_Menu->addSeparator();
        for (const auto& route : allRoutes)
        {
            auto* action = new QAction(ui->menuRouting_Menu);
            action->setText(route->name);
            action->setData(route->id);
            action->setCheckable(true);
            action->setChecked(Configs::dataManager->settingsRepo->current_route_id == route->id);
            connect(action, &QAction::triggered, this, [=,this]()
            {
                auto routeID = action->data().toInt();
                if (Configs::dataManager->settingsRepo->current_route_id == routeID) return;
                Configs::dataManager->settingsRepo->current_route_id = routeID;
                Configs::dataManager->settingsRepo->Save();
                if (Configs::dataManager->settingsRepo->started_id >= 0) profile_start(Configs::dataManager->settingsRepo->started_id);
            });
            ui->menuRouting_Menu->addAction(action);
        }
    });
    connect(ui->actionClear_Test_Result, &QAction::triggered, this, [=, this]() {
        auto entIDs = get_now_selected_list();
        auto ents = Configs::dataManager->profilesRepo->GetProfileBatch(entIDs);
        if (ents.empty()) return;
        for (const auto &ent: ents) {
            ent->ClearTestResults();
        }
        Configs::dataManager->profilesRepo->SaveBatch(ents);
        if (auto group = Configs::dataManager->groupsRepo->GetGroup(ents.first()->gid); group &&
            group->calculated_column_width.size() > ProfilesTableModel::ColTestResult)
            group->calculated_column_width[ProfilesTableModel::ColTestResult] = 0;
        refresh_proxy_list();
    });
    connect(ui->actionUrl_Test_Selected, &QAction::triggered, this, [=,this]() {
        testRunner->runUrlTests(get_now_selected_list());
    });
    connect(ui->actionUrl_Test_Group, &QAction::triggered, this, [=,this]() {
        testRunner->runUrlTests(Configs::dataManager->groupsRepo->CurrentGroup()->Profiles());
    });
    connect(ui->actionSpeedtest_Current, &QAction::triggered, this, [=,this]()
    {
        if (running != nullptr)
        {
            testRunner->runSpeedTests({}, true);
        }
    });
    connect(ui->actionSpeedtest_Selected, &QAction::triggered, this, [=,this]()
    {
        testRunner->runSpeedTests(get_now_selected_list());
    });
    connect(ui->actionSpeedtest_Group, &QAction::triggered, this, [=,this]()
    {
        testRunner->runSpeedTests(Configs::dataManager->groupsRepo->CurrentGroup()->Profiles());
    });
    connect(ui->actionResolve_Selected_Out_IP, &QAction::triggered, this, [=,this]() {
        testRunner->runIpTests(get_now_selected_list());
    });
    connect(ui->actionResolve_Out_IP, &QAction::triggered, this, [=,this]() {
        testRunner->runIpTests(Configs::dataManager->groupsRepo->CurrentGroup()->Profiles());
    });
    connect(ui->menu_stop_testing, &QAction::triggered, this, [=,this]() { testRunner->stop(); });
    auto set_selected_or_group = [=,this](int mode) {
        ui->menu_server->setProperty("selected_or_group", mode);
    };
    connect(ui->menu_server, &QMenu::aboutToHide, this, [=,this] {
        setTimeout([=,this] { set_selected_or_group(2); }, this, 200);
    });
    set_selected_or_group(2);
    connect(ui->menu_share_item, &QMenu::aboutToShow, this, [=,this] {
        QString name;
        auto selected = get_now_selected_list();

        ui->menu_export_config->setVisible(false);
        ui->actionExport_Xray_config->setVisible(false);
        if (selected.isEmpty()) return;

        auto profile = Configs::dataManager->profilesRepo->GetProfile(selected.first());
        if (!profile) return;

        if (selected.count() == 1 && profile->DisplayTestResult().trimmed().isEmpty()) {
            ui->actionCopy_Test_Result->setVisible(false);
        } else {
            ui->actionCopy_Test_Result->setVisible(true);
        }

        ui->menu_export_config->setVisible(true);
        if (profile->outbound->IsXray() || profile->type == "chain") ui->actionExport_Xray_config->setVisible(true);
    });
    connect(ui->actionExport_Xray_config, &QAction::triggered, this, [=,this]() {
        auto ents = get_now_selected_list();
        if (ents.count() != 1) return;
        auto ent = Configs::dataManager->profilesRepo->GetProfile(ents.first());
        if (ent == nullptr) return;

        auto result = Configs::BuildSingBoxConfig(ent);
        if (!result->error.isEmpty()) {
            MessageBoxWarning(tr("Failed to build the configuration"), result->error);
            return;
        }
        QString config_core = QJsonObject2QString(result->xrayConfig, true);
        QApplication::clipboard()->setText(config_core);

        QMessageBox msg(QMessageBox::Information, tr("Config copied"), config_core);
        QPushButton *button_1 = msg.addButton(tr("Copy core config"), QMessageBox::YesRole);
        QPushButton *button_2 = msg.addButton(tr("Copy test config"), QMessageBox::YesRole);
        msg.addButton(QMessageBox::Ok);
        msg.setEscapeButton(QMessageBox::Ok);
        msg.setDefaultButton(QMessageBox::Ok);
        msg.exec();
        if (msg.clickedButton() == button_1) {
            QApplication::clipboard()->setText(config_core);
        } else if (msg.clickedButton() == button_2) {
            auto res = Configs::BuildTestConfig({ent});
            if (!res->error.isEmpty()) {
                MessageBoxWarning(tr("Failed to build the test configuration"), res->error);
                return;
            }
            config_core = QJsonObject2QString(res->xrayConfig, true);
            QApplication::clipboard()->setText(config_core);
        }
    });
    connect(ui->actionCopy_Test_Result, &QAction::triggered, this, [=,this]() {
        auto ents = get_now_selected_list();
        if (ents.count() == 0 || ents.count() > 1000) return;
        auto entList = Configs::dataManager->profilesRepo->GetProfileBatch(ents);
        QString res;
        int counter = 0;
        for (auto ent : entList) {
            auto testRes = ent->DisplayTestResult();
            if (!testRes.trimmed().isEmpty()) {
                res += testRes.trimmed() + "\n";
                counter++;
            }
        }
        QApplication::clipboard()->setText(res);
        MW_show_log(QString::number(counter) + tr(" Test result(s) copied to clipboard!"));
    });
    connect(ui->actionAdd_profile_from_File, &QAction::triggered, this, [=,this]()
    {
        const auto filters = QStringList{
            tr("All files (*)"),
            tr("Config files (*.json *.conf *.txt *.yaml *.yml *.ini)"),
            tr("QR code images (*.png *.jpg *.jpeg *.bmp *.gif *.webp)"),
        };
        const auto paths = QFileDialog::getOpenFileNames(this, tr("Select profile files"), QString(), filters.join(";;"));
        if (paths.isEmpty()) return;
        importFromFiles(paths);
    });

    connect(qApp, &QGuiApplication::commitDataRequest, this, &MainWindow::on_commitDataRequest);

    auto t = new QTimer;
    connect(t, &QTimer::timeout, this, [=,this]() { refresh_status(); });
    t->start(2000);

    t = new QTimer;
    connect(t, &QTimer::timeout, this, [&] { Configs_sys::logCounter.fetchAndStoreRelaxed(0); });
    t->start(1000);

    m_proxyListRefreshDebounce = new QTimer(this);
    m_proxyListRefreshDebounce->setSingleShot(true);
    connect(m_proxyListRefreshDebounce, &QTimer::timeout, this, [this] { refresh_proxy_list({}, false); });

    connect(Stats::autoSelectorMonitor, &Stats::AutoSelectorMonitor::poolExhausted, this,
            [this](int profileID) { on_auto_selector_exhausted(profileID); }, Qt::QueuedConnection);
    connect(Stats::autoSelectorMonitor, &Stats::AutoSelectorMonitor::updated, this,
            [this] { refresh_auto_selector_view(); }, Qt::QueuedConnection);

    {
        auto* runner = Lumvane::PeriodicRunner::instance();
        const auto minutesOf = [](int v) { return v >= 30 ? v : 0; };
        runner->Add({
            tr("subscriptions"),
            [minutesOf] { return minutesOf(Configs::dataManager->settingsRepo->sub_auto_update); },
            [] { return Configs::dataManager->settingsRepo->sub_auto_update_last; },
            [](qint64 t) {
                Configs::dataManager->settingsRepo->sub_auto_update_last = t;
                Configs::dataManager->settingsRepo->Save();
            },
            [] { UI_update_all_groups(true); },
        });
        runner->Add({
            tr("routing profiles"),
            [minutesOf] { return minutesOf(Configs::dataManager->settingsRepo->route_auto_update); },
            [] { return Configs::dataManager->settingsRepo->route_auto_update_last; },
            [](qint64 t) {
                Configs::dataManager->settingsRepo->route_auto_update_last = t;
                Configs::dataManager->settingsRepo->Save();
            },
            [] { UI_update_all_remote_routes(true); },
        });
    }

    if (!Configs::dataManager->settingsRepo->flag_tray) show();

    ui->data_view->setStyleSheet("background: transparent; border: none;");
}

MainWindow::~MainWindow() {
    delete ui;
}

