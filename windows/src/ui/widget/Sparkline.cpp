#include "include/ui/widget/Sparkline.hpp"

#include <QPainter>
#include <QPainterPath>

Sparkline::Sparkline(QWidget *parent) : QWidget(parent) {
    setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Fixed);
    setFixedHeight(26);
}

void Sparkline::setColor(const QColor &c) {
    color_ = c;
    update();
}

void Sparkline::push(double value) {
    values_.append(qMax(0.0, value));
    while (values_.size() > capacity_) values_.removeFirst();
    update();
}

void Sparkline::clear() {
    values_.clear();
    update();
}

void Sparkline::paintEvent(QPaintEvent *) {
    QPainter p(this);
    p.setRenderHint(QPainter::Antialiasing, true);

    const QColor color = color_.isValid() ? color_ : palette().color(QPalette::Highlight);
    const QRectF r = QRectF(rect()).adjusted(1, 2, -1, -2);

    double peak = 0;
    for (const double v : values_) peak = qMax(peak, v);
    if (values_.size() < 2 || peak <= 0) {
        QColor base = color;
        base.setAlphaF(0.35);
        p.setPen(QPen(base, 1.2));
        p.drawLine(QPointF(r.left(), r.bottom()), QPointF(r.right(), r.bottom()));
        return;
    }

    const double stepX = r.width() / (capacity_ - 1);
    const int n = values_.size();
    QPainterPath line;
    for (int i = 0; i < n; ++i) {
        const QPointF pt(r.right() - stepX * (n - 1 - i), r.bottom() - values_[i] / peak * r.height());
        if (i == 0) line.moveTo(pt);
        else line.lineTo(pt);
    }

    QPainterPath area = line;
    area.lineTo(r.right(), r.bottom());
    area.lineTo(r.right() - stepX * (n - 1), r.bottom());
    area.closeSubpath();
    QColor fill = color;
    fill.setAlphaF(0.12);
    p.fillPath(area, fill);

    QPen pen(color, 1.6);
    pen.setJoinStyle(Qt::RoundJoin);
    p.setPen(pen);
    p.drawPath(line);
}
