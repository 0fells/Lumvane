#include "include/ui/mainwindow.h"

#include <QAbstractItemView>
#include <QApplication>
#include <QClipboard>
#include <QHeaderView>
#include <QMenu>
#include <QTableWidget>
#include <QTimer>
#include <QToolTip>
#include <memory>

void MainWindow::setupConnectionList()
{
    ui->connections->horizontalHeader()->setHighlightSections(false);
    ui->connections->setEditTriggers(QAbstractItemView::NoEditTriggers);
    ui->connections->horizontalHeader()->setSectionResizeMode(0, QHeaderView::Stretch);
    ui->connections->horizontalHeader()->setSectionResizeMode(1, QHeaderView::Stretch);
    ui->connections->horizontalHeader()->setSectionResizeMode(2, QHeaderView::ResizeToContents);
    ui->connections->horizontalHeader()->setSectionResizeMode(3, QHeaderView::ResizeToContents);
    ui->connections->horizontalHeader()->setSectionResizeMode(4, QHeaderView::ResizeToContents);
    ui->connections->horizontalHeader()->setSectionResizeMode(5, QHeaderView::ResizeToContents);
    ui->connections->verticalHeader()->hide();
    setupConnectionSortMenu();
    connect(ui->connections, &QTableWidget::cellClicked, this, [=,this](int row, int column)
    {
        auto selected = ui->connections->item(row, column);
        if (selected == nullptr) return;
        QApplication::clipboard()->setText(selected->text());
        QPoint pos = ui->connections->mapToGlobal(ui->connections->visualItemRect(selected).center());
        QToolTip::showText(pos, tr("Copied!"), this);
        auto r = ++toolTipID;
        QTimer::singleShot(1500, [=,this] {
            if (r != toolTipID)
            {
                return;
            }
            QToolTip::hideText();
        });
    });
}

void MainWindow::setupConnectionSortMenu()
{
    auto* header = ui->connections->horizontalHeader();
    header->setContextMenuPolicy(Qt::CustomContextMenu);
    connect(header, &QWidget::customContextMenuRequested, this, [=,this](const QPoint& pos)
    {
        const int columnIndex = header->logicalIndexAt(pos);
        const bool isTraffic = columnIndex == 4;
        const bool isSpeed = columnIndex == 5;
        if (!isTraffic && !isSpeed) return;

        struct SortOption { Stats::ConnectionSort value; QString label; };
        const QList<SortOption> options = isTraffic
            ? QList<SortOption>{
                { Stats::ByTraffic, tr("Total") },
                { Stats::ByDownload, tr("Downloaded") },
                { Stats::ByUpload, tr("Uploaded") } }
            : QList<SortOption>{
                { Stats::BySpeed, tr("Total") },
                { Stats::ByDownloadSpeed, tr("Download Speed") },
                { Stats::ByUploadSpeed, tr("Upload Speed") } };

        QMenu menu(this);
        auto* sortByLabel = menu.addAction(tr("Sort By:"));
        sortByLabel->setEnabled(false);

        const auto current = Stats::connection_lister->getSort();
        for (const auto& opt : options)
        {
            auto* act = menu.addAction(opt.label);
            act->setData(static_cast<int>(opt.value));
            act->setCheckable(true);
            act->setChecked(current == opt.value);
        }

        auto* chosen = menu.exec(header->mapToGlobal(pos));
        if (chosen == nullptr || !chosen->data().isValid()) return;

        Stats::connection_lister->setSort(static_cast<Stats::ConnectionSort>(chosen->data().toInt()));
        Stats::connection_lister->ForceUpdate();
    });
}

void MainWindow::UpdateConnectionList(const QMap<QString, Stats::ConnectionMetadata>& toUpdate, const QMap<QString, Stats::ConnectionMetadata>& toAdd)
{
    connectionListMu.lock();
    ui->connections->setUpdatesEnabled(false);
    for (int row=0;row<ui->connections->rowCount();row++)
    {
        const auto key = ui->connections->item(row, 0)->data(Stats::IDKEY).toString();
        if (!toUpdate.contains(key))
        {
            ui->connections->removeRow(row);
            row--;
            continue;
        }

        const auto conn = toUpdate[key];
        ui->connections->item(row, 0)->setText(DisplayDest(conn.dest, conn.domain));

        ui->connections->item(row, 1)->setText(conn.process);

        auto prot = conn.network;
        if (!conn.protocol.isEmpty()) prot += " ("+conn.protocol+")";
        ui->connections->item(row, 2)->setText(prot);

        ui->connections->item(row, 3)->setText(conn.outbound);

        ui->connections->item(row, 4)->setText("↓" + ReadableSize(conn.download) + "  ↑" + ReadableSize(conn.upload));

        ui->connections->item(row, 5)->setText("↓" + formatRate(conn.downloadSpeed) + "  ↑" + formatRate(conn.uploadSpeed));
    }
    int row = ui->connections->rowCount();
    for (const auto& conn : toAdd)
    {
        ui->connections->insertRow(row);
        auto f0 = std::make_unique<QTableWidgetItem>();
        f0->setData(Stats::IDKEY, conn.id);

        auto f = f0->clone();
        f->setText(DisplayDest(conn.dest, conn.domain));
        ui->connections->setItem(row, 0, f);

        f = f0->clone();
        f->setText(conn.process);
        ui->connections->setItem(row, 1, f);

        f = f0->clone();
        auto prot = conn.network;
        if (!conn.protocol.isEmpty()) prot += " ("+conn.protocol+")";
        f->setText(prot);
        ui->connections->setItem(row, 2, f);

        f = f0->clone();
        f->setText(conn.outbound);
        ui->connections->setItem(row, 3, f);

        f = f0->clone();
        f->setText("↓" + ReadableSize(conn.download) + "  ↑" + ReadableSize(conn.upload));
        ui->connections->setItem(row, 4, f);

        f = f0->clone();
        f->setText("↓" + formatRate(conn.downloadSpeed) + "  ↑" + formatRate(conn.uploadSpeed));
        ui->connections->setItem(row, 5, f);

        row++;
    }
    ui->connections->setUpdatesEnabled(true);
    connectionListMu.unlock();
}

void MainWindow::UpdateConnectionListWithRecreate(const QList<Stats::ConnectionMetadata>& connections)
{
    connectionListMu.lock();
    ui->connections->setUpdatesEnabled(false);
    ui->connections->setRowCount(0);
    int row=0;
    for (const auto& conn : connections)
    {
        ui->connections->insertRow(row);
        auto f0 = std::make_unique<QTableWidgetItem>();
        f0->setData(Stats::IDKEY, conn.id);

        auto f = f0->clone();
        f->setText(DisplayDest(conn.dest, conn.domain));
        ui->connections->setItem(row, 0, f);

        f = f0->clone();
        f->setText(conn.process);
        ui->connections->setItem(row, 1, f);

        f = f0->clone();
        auto prot = conn.network;
        if (!conn.protocol.isEmpty()) prot += " ("+conn.protocol+")";
        f->setText(prot);
        ui->connections->setItem(row, 2, f);

        f = f0->clone();
        f->setText(conn.outbound);
        ui->connections->setItem(row, 3, f);

        f = f0->clone();
        f->setText("↓" + ReadableSize(conn.download) + "  ↑" + ReadableSize(conn.upload));
        ui->connections->setItem(row, 4, f);

        f = f0->clone();
        f->setText("↓" + formatRate(conn.downloadSpeed) + "  ↑" + formatRate(conn.uploadSpeed));
        ui->connections->setItem(row, 5, f);

        row++;
    }
    ui->connections->setUpdatesEnabled(true);
    connectionListMu.unlock();
}
