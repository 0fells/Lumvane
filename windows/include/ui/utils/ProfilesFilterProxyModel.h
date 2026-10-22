#pragma once

#include <QSortFilterProxyModel>
#include <QString>

class ProfilesTableModel;

class ProfilesFilterProxyModel : public QSortFilterProxyModel {
    Q_OBJECT
public:
    explicit ProfilesFilterProxyModel(QObject *parent = nullptr);

    void setFilters(const QString &type, const QString &address, const QString &name, const QString &country);
    void setSearch(const QString &text);
    bool hasActiveFilter() const;

    ProfilesTableModel *profilesModel() const;

    int toSourceRow(int proxyRow) const;
    int toProxyRow(int sourceRow) const;

protected:
    bool filterAcceptsRow(int sourceRow, const QModelIndex &sourceParent) const override;

private:
    bool portMatches(int port) const;

    QString m_type;
    QString m_address;
    QString m_name;
    QString m_country;
    QString m_search;
};
