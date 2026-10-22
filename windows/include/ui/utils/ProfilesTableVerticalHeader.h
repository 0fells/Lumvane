#pragma once

#include <QHeaderView>

class ProfilesTableModel;
class ProfilesFilterProxyModel;

class ProfilesTableVerticalHeader : public QHeaderView {
    Q_OBJECT
public:
    explicit ProfilesTableVerticalHeader(QWidget *parent = nullptr);

    void setProfilesModel(ProfilesTableModel *model, ProfilesFilterProxyModel *proxy = nullptr);
    ProfilesTableModel *profilesModel() const { return m_model; }

    void updateWidthFromRowCount();

protected:
    void paintSection(QPainter *painter, const QRect &rect, int logicalIndex) const override;

private:
    ProfilesTableModel *m_model = nullptr;
    ProfilesFilterProxyModel *m_proxy = nullptr;
};
