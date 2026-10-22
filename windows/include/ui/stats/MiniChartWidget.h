#pragma once

#include <QWidget>
#include <QVector>
#include <QColor>

#include <functional>

class MiniChartWidget : public QWidget {
    Q_OBJECT

public:
    explicit MiniChartWidget(QWidget* parent = nullptr);

    void setCapacity(int n);
    void setColors(const QColor& primary, const QColor& secondary);
    void setMaxValue(double m);
    void setFormatter(std::function<QString(double)> formatter);
    void setCaption(const QString& caption);
    void push(double primary, double secondary);
    void clear();

protected:
    void paintEvent(QPaintEvent* event) override;

private:
    QVector<double> a_;
    QVector<double> b_;
    int cap_ = 60;
    double fixedMax_ = -1.0;
    QColor primary_;
    QColor secondary_;
    std::function<QString(double)> formatter_;
    QString caption_;
};
