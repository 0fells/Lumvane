#include "include/ui/stats/dialog_traffic_stats.h"

#include "include/ui/stats/TrafficChartWidget.h"
#include "include/ui/setting/ThemeManager.hpp"
#include "include/ui/utils/ProfileTypeDelegate.h"

#include "include/database/DatabaseManager.h"
#include "include/database/GroupsRepo.h"
#include "include/database/ProfilesRepo.h"
#include "include/database/TrafficStatsRepo.h"
#include "include/database/entities/Profile.h"
#include "include/stats/traffic/TrafficStatsManager.hpp"
#include "include/global/Utils.hpp"

#include <QComboBox>
#include <QDateTime>
#include <QHash>
#include <QHeaderView>
#include <QLabel>
#include <QPainter>
#include <QPushButton>
#include <QTabWidget>
#include <QTableWidget>

#include <algorithm>

#include "include/configs/generate.h"

namespace {
    constexpr int kMaxBreakdownRows = 9;

    constexpr int kOtherRole = Qt::UserRole + 1;

    class TrafficStatsItem : public QTableWidgetItem {
    public:
        explicit TrafficStatsItem(const QString& text, bool other = false) : QTableWidgetItem(text) {
            QTableWidgetItem::setData(kOtherRole, other);
        }
        bool operator<(const QTableWidgetItem& other) const override {
            const bool thisOther = data(kOtherRole).toBool(), otherOther = other.data(kOtherRole).toBool();
            if (thisOther != otherOther) {
                const auto order = tableWidget() ? tableWidget()->horizontalHeader()->sortIndicatorOrder() : Qt::AscendingOrder;
                return order == Qt::AscendingOrder ? otherOther : thisOther;
            }
            return lessThan(other);
        }

    protected:
        virtual bool lessThan(const QTableWidgetItem& other) const { return QTableWidgetItem::operator<(other); }
    };

    class TrafficStatsSizeItem : public TrafficStatsItem {
    public:
        explicit TrafficStatsSizeItem(long long value, bool other = false) : TrafficStatsItem(ReadableSize(value), other) {
            QTableWidgetItem::setData(Qt::UserRole, QVariant::fromValue<qlonglong>(value));
            setTextAlignment(Qt::AlignRight | Qt::AlignVCenter);
        }

    protected:
        bool lessThan(const QTableWidgetItem& other) const override {
            return data(Qt::UserRole).toLongLong() < other.data(Qt::UserRole).toLongLong();
        }
    };

    class BreakdownHeader : public QHeaderView {
    public:
        explicit BreakdownHeader(QWidget* parent) : QHeaderView(Qt::Horizontal, parent) {}

    protected:
        void initStyleOptionForIndex(QStyleOptionHeader* option, int logicalIndex) const override {
            QHeaderView::initStyleOptionForIndex(option, logicalIndex);
            option->sortIndicator = QStyleOptionHeader::None;
        }

        void paintSection(QPainter* painter, const QRect& rect, int logicalIndex) const override {
            QHeaderView::paintSection(painter, rect, logicalIndex);
            if (!isSortIndicatorShown() || sortIndicatorSection() != logicalIndex || !model()) return;

            const QString label = model()->headerData(logicalIndex, orientation()).toString();
            const auto align = Qt::Alignment(model()->headerData(logicalIndex, orientation(), Qt::TextAlignmentRole).toInt());
            const int labelW = fontMetrics().horizontalAdvance(label);
            constexpr int kGap = 6, kW = 8, kH = 4;
            const int x = (align & Qt::AlignRight) ? rect.right() - ProfileTextDelegate::TextPadding - labelW - kGap - kW
                                                   : rect.left() + ProfileTextDelegate::TextPadding + labelW + kGap;
            const qreal y = rect.center().y() - kH / 2.0 + 0.5;
            const bool up = sortIndicatorOrder() == Qt::AscendingOrder;
            const QPointF a(x, up ? y + kH : y), b(x + kW / 2.0, up ? y : y + kH), c(x + kW, up ? y + kH : y);

            painter->save();
            painter->setClipRect(rect);
            painter->setRenderHint(QPainter::Antialiasing);
            painter->setPen(QPen(ThemeManager::Tokens().muted, 1.5, Qt::SolidLine, Qt::RoundCap, Qt::RoundJoin));
            painter->drawPolyline(QPolygonF({a, b, c}));
            painter->restore();
        }
    };

    void setupBreakdownTable(QTableWidget* table, int firstFigureColumn) {
        auto* header = new BreakdownHeader(table);
        header->setHighlightSections(false);
        header->setSectionsClickable(true);
        header->setSortIndicatorShown(true);
        table->setHorizontalHeader(header);
        for (int c = 0; c < table->columnCount(); ++c) {
            if (auto* item = table->horizontalHeaderItem(c))
                item->setTextAlignment((c < firstFigureColumn ? Qt::AlignLeft : Qt::AlignRight) | Qt::AlignVCenter);
            header->setSectionResizeMode(c, c < firstFigureColumn ? QHeaderView::Stretch : QHeaderView::ResizeToContents);
        }
        table->setItemDelegate(new ProfileTextDelegate(table));
    }
}

DialogTrafficStats::DialogTrafficStats(QWidget* parent) : QDialog(parent), ui(new Ui::DialogTrafficStats) {
    ui->setupUi(this);

    ui->verticalLayout->setStretch(1, 2);
    ui->verticalLayout->setStretch(2, 3);

    setupBreakdownTable(ui->profileTable, 2);
    setupBreakdownTable(ui->appTable, 1);

    connect(ui->refreshBtn, &QPushButton::clicked, this, [this] { refresh(); });
    connect(ui->periodCombo, &QComboBox::currentIndexChanged, this, [this](int) { refresh(); });
    connect(ui->tabs, &QTabWidget::currentChanged, this, [this](int) { refresh(); });

    refresh();
}

DialogTrafficStats::~DialogTrafficStats() {
    delete ui;
}

long long DialogTrafficStats::selectedWindowSecs() const {
    switch (ui->periodCombo->currentIndex()) {
        case 1: return 7LL * 86400LL;
        case 2: return 30LL * 86400LL;
        case 3: return 90LL * 86400LL;
        case 0:
        default: return 24LL * 3600LL;
    }
}

long long DialogTrafficStats::selectedBucketSecs() const {
    return ui->periodCombo->currentIndex() == 0 ? 3600LL : 86400LL;
}

void DialogTrafficStats::refresh() {
    auto* repo = Configs::dataManager ? Configs::dataManager->trafficStatsRepo.get() : nullptr;
    if (!repo) return;

    Stats::trafficStatsManager->Flush();

    const long long now = QDateTime::currentSecsSinceEpoch();
    const long long window = selectedWindowSecs();
    const long long bucket = selectedBucketSecs();
    const long long from = now - window;
    const long long tzOffset = QDateTime::currentDateTime().offsetFromUtc();

    populateProfileTable(from, now);
    populateAppTable(from, now);

    const bool byApp = ui->tabs->currentIndex() == 1;
    const auto series = byApp ? repo->QueryAppSeries(from, now, bucket, tzOffset)
                              : repo->QueryConfigSeries(from, now, bucket, tzOffset);

    QHash<long long, Configs::TrafficSeriesPoint> byBucket;
    byBucket.reserve(series.size());
    long long totalUp = 0, totalDown = 0;
    for (const auto& pt : series) {
        byBucket.insert(pt.bucket_start, pt);
        totalUp += pt.up;
        totalDown += pt.down;
    }

    const long long alignedFrom = ((from + tzOffset) / bucket) * bucket - tzOffset;
    QList<TrafficChartWidget::Bar> bars;
    for (long long b = alignedFrom; b < now; b += bucket) {
        TrafficChartWidget::Bar bar;
        bar.bucketStart = b;
        if (const auto it = byBucket.constFind(b); it != byBucket.constEnd()) {
            bar.down = it->down;
            bar.up = it->up;
        }
        bar.label = bucket >= 86400LL ? QDateTime::fromSecsSinceEpoch(b).toString("MM/dd")
                                      : QDateTime::fromSecsSinceEpoch(b).toString("HH:mm");
        bars.append(bar);
    }
    const int stride = qMax(1, (static_cast<int>(bars.size()) + 7) / 8);
    ui->chart->setData(bars, stride, bucket);

    ui->summaryLabel->setText(tr("Download: %1     Upload: %2     Total: %3")
                               .arg(ReadableSize(totalDown), ReadableSize(totalUp),
                                    ReadableSize(totalDown + totalUp)));
}

void DialogTrafficStats::populateProfileTable(long long fromSecs, long long toSecs) {
    auto* repo = Configs::dataManager->trafficStatsRepo.get();
    auto usage = repo->QueryConfigUsage(fromSecs, toSecs);
    QHash<int, Configs::ConfigMetaRow> meta;
    for (const auto& m : repo->GetAllConfigMeta()) meta.insert(m.profile_id, m);

    std::sort(usage.begin(), usage.end(), [](const Configs::ConfigUsage& a, const Configs::ConfigUsage& b) {
        return (a.down + a.up) > (b.down + b.up);
    });
    const int count = static_cast<int>(usage.size());
    const int shown = qMin(count, kMaxBreakdownRows);
    const bool hasOther = count > kMaxBreakdownRows;
    long long otherDown = 0, otherUp = 0;
    for (int i = shown; i < count; ++i) {
        otherDown += usage[i].down;
        otherUp += usage[i].up;
    }

    ui->profileTable->setSortingEnabled(false);
    ui->profileTable->setRowCount(shown + (hasOther ? 1 : 0));
    for (int i = 0; i < shown; ++i) {
        const auto& u = usage[i];
        QString name, group;
        if (const auto it = meta.constFind(u.profile_id); it != meta.constEnd()) {
            name = it->name;
            group = it->group_name;
        }
        if (name.isEmpty()) {
            if (u.profile_id == Stats::DIRECT_STAT_PROFILE_ID) {
                name = tr("Direct");
            } else if (const auto prof = Configs::dataManager->profilesRepo->GetProfile(u.profile_id)) {
                name = prof->name;
            } else if (u.profile_id == Configs::warpProfileID) {
                name = tr("Built-in Warp");
            } else {
                name = tr("Profile #%1 (deleted)").arg(u.profile_id);
            }
        }
        if (group.isEmpty()) {
            if (const auto prof = Configs::dataManager->profilesRepo->GetProfile(u.profile_id)) {
                if (const auto g = Configs::dataManager->groupsRepo->GetGroup(prof->gid)) group = g->name;
            }
        }
        ui->profileTable->setItem(i, 0, new TrafficStatsItem(name));
        ui->profileTable->setItem(i, 1, new TrafficStatsItem(group));
        ui->profileTable->setItem(i, 2, new TrafficStatsSizeItem(u.down));
        ui->profileTable->setItem(i, 3, new TrafficStatsSizeItem(u.up));
        ui->profileTable->setItem(i, 4, new TrafficStatsSizeItem(u.down + u.up));
    }
    if (hasOther) {
        ui->profileTable->setItem(shown, 0, new TrafficStatsItem(tr("Other"), true));
        ui->profileTable->setItem(shown, 1, new TrafficStatsItem(QString(), true));
        ui->profileTable->setItem(shown, 2, new TrafficStatsSizeItem(otherDown, true));
        ui->profileTable->setItem(shown, 3, new TrafficStatsSizeItem(otherUp, true));
        ui->profileTable->setItem(shown, 4, new TrafficStatsSizeItem(otherDown + otherUp, true));
    }
    ui->profileTable->setSortingEnabled(true);
    ui->profileTable->sortByColumn(4, Qt::DescendingOrder);
}

void DialogTrafficStats::populateAppTable(long long fromSecs, long long toSecs) {
    auto* repo = Configs::dataManager->trafficStatsRepo.get();
    auto usage = repo->QueryAppUsage(fromSecs, toSecs);

    std::sort(usage.begin(), usage.end(), [](const Configs::AppUsage& a, const Configs::AppUsage& b) {
        return (a.down + a.up) > (b.down + b.up);
    });
    const int count = static_cast<int>(usage.size());
    const int shown = qMin(count, kMaxBreakdownRows);
    const bool hasOther = count > kMaxBreakdownRows;
    long long otherDown = 0, otherUp = 0;
    for (int i = shown; i < count; ++i) {
        otherDown += usage[i].down;
        otherUp += usage[i].up;
    }

    ui->appTable->setSortingEnabled(false);
    ui->appTable->setRowCount(shown + (hasOther ? 1 : 0));
    for (int i = 0; i < shown; ++i) {
        const auto& u = usage[i];
        QString name = u.process_name.isEmpty() ? tr("Unknown") : u.process_name;
        ui->appTable->setItem(i, 0, new TrafficStatsItem(name));
        ui->appTable->setItem(i, 1, new TrafficStatsSizeItem(u.down));
        ui->appTable->setItem(i, 2, new TrafficStatsSizeItem(u.up));
        ui->appTable->setItem(i, 3, new TrafficStatsSizeItem(u.down + u.up));
    }
    if (hasOther) {
        ui->appTable->setItem(shown, 0, new TrafficStatsItem(tr("Other"), true));
        ui->appTable->setItem(shown, 1, new TrafficStatsSizeItem(otherDown, true));
        ui->appTable->setItem(shown, 2, new TrafficStatsSizeItem(otherUp, true));
        ui->appTable->setItem(shown, 3, new TrafficStatsSizeItem(otherDown + otherUp, true));
    }
    ui->appTable->setSortingEnabled(true);
    ui->appTable->sortByColumn(3, Qt::DescendingOrder);
}
