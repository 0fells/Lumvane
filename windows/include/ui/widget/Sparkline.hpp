#pragma once

#include <QColor>
#include <QVector>
#include <QWidget>

class Sparkline : public QWidget {
    Q_OBJECT

public:
    explicit Sparkline(QWidget *parent = nullptr);

    void setColor(const QColor &c);
    void push(double value);
    void clear();

    QSize sizeHint() const override { return {180, 26}; }

protected:
    void paintEvent(QPaintEvent *) override;

private:
    QVector<double> values_;
    int capacity_ = 40;
    QColor color_;
};
