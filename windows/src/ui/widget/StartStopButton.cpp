#include "include/ui/widget/StartStopButton.hpp"
#include "include/ui/setting/ThemeManager.hpp"

#include <QConicalGradient>
#include <QEvent>
#include <QLinearGradient>
#include <QPainter>
#include <QPainterPath>
#include <QPixmap>
#include <QPropertyAnimation>
#include <QRadialGradient>
#include <QStyle>
#include <QStyleOptionToolButton>

StartStopButton::StartStopButton(QWidget *parent) : QToolButton(parent) {
    setFocusPolicy(Qt::NoFocus);
    setSizePolicy(QSizePolicy::Fixed, QSizePolicy::Fixed);
    setAttribute(Qt::WA_Hover, true);
    m_ringColor = idleRingColor();

    m_morphAnim = new QPropertyAnimation(this, "morph", this);
    m_dimAnim = new QPropertyAnimation(this, "dim", this);
    m_pressAnim = new QPropertyAnimation(this, "press", this);
    m_ringColorAnim = new QPropertyAnimation(this, "ringColor", this);

    m_spinAnim = new QPropertyAnimation(this, "spin", this);
    m_spinAnim->setStartValue(0.0);
    m_spinAnim->setEndValue(360.0);
    m_spinAnim->setDuration(900);
    m_spinAnim->setLoopCount(-1);
    m_spinAnim->setEasingCurve(QEasingCurve::Linear);

    connect(this, &QAbstractButton::pressed, this, [this] { animate(m_pressAnim, 1.0, 110); });
    connect(this, &QAbstractButton::released, this, [this] { animate(m_pressAnim, 0.0, 160); });

    m_state = State::Disabled;
    applyState(false);
}

QSize StartStopButton::sizeHint() const {
    return {72, 72};
}

void StartStopButton::setState(State s) {
    if (s == m_state) return;
    m_state = s;
    applyState(true);
}

void StartStopButton::setMode(Mode m) {
    if (m == m_mode) return;
    m_mode = m;
    if (m_state == State::Running) animate(m_ringColorAnim, targetRingColor(), 320);
}

void StartStopButton::applyState(bool animated) {
    const bool interactive = (m_state == State::Idle || m_state == State::Running);
    setEnabled(interactive);
    setCursor(interactive ? Qt::PointingHandCursor : Qt::ArrowCursor);

    switch (m_state) {
        case State::Disabled: setToolTip(tr("Select a profile to start")); break;
        case State::Idle: setToolTip(tr("Start")); break;
        case State::Connecting: setToolTip(tr("Connecting…")); break;
        case State::Running: setToolTip(tr("Stop")); break;
        case State::Disconnecting: setToolTip(tr("Stopping…")); break;
    }
    setAccessibleName(toolTip());

    const qreal morphTarget = (m_state == State::Running || m_state == State::Disconnecting) ? 1.0 : 0.0;
    const qreal dimTarget = (m_state == State::Disabled) ? 0.45 : 1.0;
    const QColor ringTarget = targetRingColor();

    if (animated) {
        animate(m_morphAnim, morphTarget, 300);
        animate(m_dimAnim, dimTarget, 220);
        animate(m_ringColorAnim, ringTarget, 320);
    } else {
        m_morphAnim->stop();
        m_dimAnim->stop();
        m_ringColorAnim->stop();
        m_morph = morphTarget;
        m_dim = dimTarget;
        m_ringColor = ringTarget;
    }

    updateLoops();
    update();
}

void StartStopButton::animate(QPropertyAnimation *anim, const QVariant &to, int duration) {
    anim->stop();
    anim->setDuration(duration);
    anim->setEasingCurve(QEasingCurve::InOutCubic);
    anim->setStartValue(property(anim->propertyName().constData()));
    anim->setEndValue(to);
    anim->start();
}

void StartStopButton::setLoopRunning(QPropertyAnimation *anim, bool running) {
    if (running) {
        if (anim->state() != QAbstractAnimation::Running) anim->start();
        return;
    }
    anim->stop();
    if (anim == m_spinAnim) m_spin = 0.0;
    update();
}


void StartStopButton::updateLoops() {
    const bool spinning = m_state == State::Connecting || m_state == State::Disconnecting;
    setLoopRunning(m_spinAnim, m_shown && spinning);
}

void StartStopButton::showEvent(QShowEvent *e) {
    QToolButton::showEvent(e);
    m_shown = true;
    updateLoops();
}

void StartStopButton::hideEvent(QHideEvent *e) {
    m_shown = false;
    setLoopRunning(m_spinAnim, false);
    QToolButton::hideEvent(e);
}

void StartStopButton::changeEvent(QEvent *e) {
    switch (e->type()) {
        case QEvent::StyleChange:
        case QEvent::PaletteChange:
        case QEvent::ThemeChange:
            m_ringColor = targetRingColor();
            update();
            break;
        default:
            break;
    }
    QToolButton::changeEvent(e);
}


QColor StartStopButton::modeColor(Mode m) const {
    const bool dark = ThemeManager::Tokens().dark;
    switch (m) {
        case Mode::Core: return ThemeManager::Tokens().good;
        case Mode::SystemProxy: return QColor(dark ? "#4DA3FF" : "#1F6FE5");
        case Mode::Tun: return QColor(dark ? "#FF6B6B" : "#D92B2B");
        case Mode::Dns: return QColor(dark ? "#F5B35C" : "#C88A00");
        case Mode::SystemProxyDns: return QColor(dark ? "#B48CFF" : "#7C4DDB");
        case Mode::Off:
        default: return idleRingColor();
    }
}

QColor StartStopButton::idleRingColor() const {
    return ThemeManager::Tokens().border;
}

QColor StartStopButton::glyphColor() const {
    const auto tk = ThemeManager::Tokens();
    if (m_state == State::Running) return tk.onAccent;
    if (m_state == State::Disabled) return tk.muted;
    return tk.accent;
}

QColor StartStopButton::targetRingColor() const {
    switch (m_state) {
        case State::Connecting:
        case State::Disconnecting: return ThemeManager::Tokens().accent;
        case State::Running: return modeColor(m_mode);
        default: return idleRingColor();
    }
}

void StartStopButton::ensureChromeCache() {
    m_chromeCache = QPixmap();
}

void StartStopButton::paintEvent(QPaintEvent *) {
    QPainter p(this);
    p.setRenderHint(QPainter::Antialiasing, true);

    const auto tk = ThemeManager::Tokens();
    const QRectF cr = rect();
    const qreal D = qMin(cr.width(), cr.height());
    const QPointF c = cr.center();

    const qreal scale = 1.0 - 0.05 * m_press;
    p.translate(c);
    p.scale(scale, scale);
    p.translate(-c);

    const qreal ringW = qMax(2.5, D * 0.05);
    const qreal gap = qMax(2.5, D * 0.05);
    const qreal R = D / 2.0 - ringW - gap;
    const qreal ringR = R + gap + ringW / 2.0;
    const bool busy = m_state == State::Connecting || m_state == State::Disconnecting;
    const bool hover = underMouse() && isEnabled();

    p.setOpacity(m_dim);

    if (m_state == State::Running) {
        p.setBrush(Qt::NoBrush);
        p.setPen(QPen(m_ringColor, ringW));
        p.drawEllipse(c, ringR, ringR);

        QColor fill = tk.accent;
        if (hover) fill = tk.dark ? fill.lighter(112) : fill.darker(112);
        p.setPen(Qt::NoPen);
        p.setBrush(fill);
        p.drawEllipse(c, R, R);
    } else {
        p.setPen(QPen(tk.border, qMax(1.5, D * 0.03)));
        p.setBrush(hover ? tk.subtle : tk.surface);
        p.drawEllipse(c, R, R);

        if (busy) {
            QColor track = m_ringColor;
            track.setAlphaF(0.20);
            p.setBrush(Qt::NoBrush);
            p.setPen(QPen(track, ringW));
            p.drawEllipse(c, ringR, ringR);
            QPen arcPen(m_ringColor, ringW);
            arcPen.setCapStyle(Qt::RoundCap);
            p.setPen(arcPen);
            const QRectF rr(c.x() - ringR, c.y() - ringR, 2 * ringR, 2 * ringR);
            p.drawArc(rr, static_cast<int>(-m_spin * 16), -110 * 16);
        }
    }

    if (hasFocus()) {
        QColor focus = tk.accent;
        focus.setAlphaF(0.6);
        p.setBrush(Qt::NoBrush);
        p.setPen(QPen(focus, 1.5, Qt::DotLine));
        p.drawEllipse(c, D / 2.0 - 1.0, D / 2.0 - 1.0);
    }

    const qreal gs = R * 0.42;
    QPen gpen(glyphColor(), qMax(2.0, D * 0.045));
    gpen.setCapStyle(Qt::RoundCap);
    p.setOpacity(m_dim * (busy ? 0.55 : 1.0));
    p.setPen(gpen);
    p.setBrush(Qt::NoBrush);
    p.drawLine(c + QPointF(0, -gs * 1.15), c + QPointF(0, -gs * 0.2));
    const QRectF ar(c.x() - gs, c.y() - gs, 2 * gs, 2 * gs);
    p.drawArc(ar, 125 * 16, 290 * 16);
}
