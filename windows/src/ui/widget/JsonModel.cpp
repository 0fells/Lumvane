/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2011 SCHUTZ Sacha
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#include "include/ui/widget/JsonModel.hpp"

#include <QDebug>
#include <QFile>

JsonTreeItem::JsonTreeItem(JsonTreeItem *parent) {
    mParent = parent;
}

JsonTreeItem::~JsonTreeItem() {
    qDeleteAll(mChilds);
}

void JsonTreeItem::appendChild(JsonTreeItem *item) {
    mChilds.append(item);
}

JsonTreeItem *JsonTreeItem::child(int row) {
    return mChilds.value(row);
}

JsonTreeItem *JsonTreeItem::parent() {
    return mParent;
}

int JsonTreeItem::childCount() const {
    return mChilds.count();
}

int JsonTreeItem::row() const {
    if (mParent)
        return mParent->mChilds.indexOf(const_cast<JsonTreeItem *>(this));

    return 0;
}

void JsonTreeItem::setKey(const QString &key) {
    mKey = key;
}

void JsonTreeItem::setValue(const QString &value) {
    mValue = value;
}

void JsonTreeItem::setType(const QJsonValue::Type &type) {
    mType = type;
}

QString JsonTreeItem::key() const {
    return mKey;
}

QString JsonTreeItem::value() const {
    return mValue;
}

QJsonValue::Type JsonTreeItem::type() const {
    return mType;
}

JsonTreeItem *JsonTreeItem::load(const QJsonValue &value, JsonTreeItem *parent) {
    JsonTreeItem *rootItem = new JsonTreeItem(parent);
    rootItem->setKey("root");

    if (value.isObject()) {
        for (QString key: value.toObject().keys()) {
            QJsonValue v = value.toObject().value(key);
            JsonTreeItem *child = load(v, rootItem);
            child->setKey(key);
            child->setType(v.type());
            rootItem->appendChild(child);
        }
    } else if (value.isArray()) {
        int index = 0;

        for (QJsonValue v: value.toArray()) {
            JsonTreeItem *child = load(v, rootItem);
            child->setKey(QString::number(index));
            child->setType(v.type());
            rootItem->appendChild(child);
            ++index;
        }
    } else {
        rootItem->setValue(value.toVariant().toString());
        rootItem->setType(value.type());
    }

    return rootItem;
}

JsonModel::JsonModel(QObject *parent) : QAbstractItemModel(parent), mRootItem{new JsonTreeItem} {
    mHeaders.append("key");
    mHeaders.append("value");
}

JsonModel::JsonModel(const QString &fileName, QObject *parent) : QAbstractItemModel(parent), mRootItem{new JsonTreeItem} {
    mHeaders.append("key");
    mHeaders.append("value");
    load(fileName);
}

JsonModel::JsonModel(QIODevice *device, QObject *parent) : QAbstractItemModel(parent), mRootItem{new JsonTreeItem} {
    mHeaders.append("key");
    mHeaders.append("value");
    load(device);
}

JsonModel::JsonModel(const QByteArray &json, QObject *parent) : QAbstractItemModel(parent), mRootItem{new JsonTreeItem} {
    mHeaders.append("key");
    mHeaders.append("value");
    loadJson(json);
}

JsonModel::~JsonModel() {
    delete mRootItem;
}

bool JsonModel::load(const QString &fileName) {
    QFile file(fileName);
    bool success = false;

    if (file.open(QIODevice::ReadOnly)) {
        success = load(&file);
        file.close();
    } else
        success = false;

    return success;
}

bool JsonModel::load(QIODevice *device) {
    return loadJson(device->readAll());
}

bool JsonModel::loadJson(const QByteArray &json) {
    auto const &jdoc = QJsonDocument::fromJson(json);

    if (!jdoc.isNull()) {
        beginResetModel();
        delete mRootItem;

        if (jdoc.isArray()) {
            mRootItem = JsonTreeItem::load(QJsonValue(jdoc.array()));
            mRootItem->setType(QJsonValue::Array);
        } else {
            mRootItem = JsonTreeItem::load(QJsonValue(jdoc.object()));
            mRootItem->setType(QJsonValue::Object);
        }

        endResetModel();
        return true;
    }

    qDebug() << Q_FUNC_INFO << "cannot load json";
    return false;
}

QVariant JsonModel::data(const QModelIndex &index, int role) const {
    if (!index.isValid())
        return QVariant();

    JsonTreeItem *item = static_cast<JsonTreeItem *>(index.internalPointer());

    if (role == Qt::DisplayRole) {
        if (index.column() == 0)
            return QString("%1").arg(item->key());

        if (index.column() == 1)
            return QString("%1").arg(item->value());
    } else if (Qt::EditRole == role) {
        if (index.column() == 1) {
            return QString("%1").arg(item->value());
        }
    }

    return QVariant();
}

bool JsonModel::setData(const QModelIndex &index, const QVariant &value, int role) {
    int col = index.column();

    if (Qt::EditRole == role) {
        if (col == 1) {
            JsonTreeItem *item = static_cast<JsonTreeItem *>(index.internalPointer());
            item->setValue(value.toString());
            emit dataChanged(index, index, {Qt::EditRole});
            return true;
        }
    }

    return false;
}

QVariant JsonModel::headerData(int section, Qt::Orientation orientation, int role) const {
    if (role != Qt::DisplayRole)
        return QVariant();

    if (orientation == Qt::Horizontal) {
        return mHeaders.value(section);
    } else
        return QVariant();
}

QModelIndex JsonModel::index(int row, int column, const QModelIndex &parent) const {
    if (!hasIndex(row, column, parent))
        return QModelIndex();

    JsonTreeItem *parentItem;

    if (!parent.isValid())
        parentItem = mRootItem;
    else
        parentItem = static_cast<JsonTreeItem *>(parent.internalPointer());

    JsonTreeItem *childItem = parentItem->child(row);

    if (childItem)
        return createIndex(row, column, childItem);
    else
        return QModelIndex();
}

QModelIndex JsonModel::parent(const QModelIndex &index) const {
    if (!index.isValid())
        return QModelIndex();

    JsonTreeItem *childItem = static_cast<JsonTreeItem *>(index.internalPointer());
    JsonTreeItem *parentItem = childItem->parent();

    if (parentItem == mRootItem)
        return QModelIndex();

    return createIndex(parentItem->row(), 0, parentItem);
}

int JsonModel::rowCount(const QModelIndex &parent) const {
    JsonTreeItem *parentItem;

    if (parent.column() > 0)
        return 0;

    if (!parent.isValid())
        parentItem = mRootItem;
    else
        parentItem = static_cast<JsonTreeItem *>(parent.internalPointer());

    return parentItem->childCount();
}

int JsonModel::columnCount(const QModelIndex &parent) const {
    Q_UNUSED(parent)
    return 2;
}

Qt::ItemFlags JsonModel::flags(const QModelIndex &index) const {
    int col = index.column();
    auto item = static_cast<JsonTreeItem *>(index.internalPointer());
    auto isArray = QJsonValue::Array == item->type();
    auto isObject = QJsonValue::Object == item->type();

    if ((col == 1) && !(isArray || isObject)) {
        return Qt::ItemIsEditable | QAbstractItemModel::flags(index);
    } else {
        return QAbstractItemModel::flags(index);
    }
}

QJsonDocument JsonModel::json() const {
    auto v = genJson(mRootItem);
    QJsonDocument doc;

    if (v.isObject()) {
        doc = QJsonDocument(v.toObject());
    } else {
        doc = QJsonDocument(v.toArray());
    }

    return doc;
}

QJsonValue JsonModel::genJson(JsonTreeItem *item) const {
    auto type = item->type();
    int nchild = item->childCount();

    if (QJsonValue::Object == type) {
        QJsonObject jo;

        for (int i = 0; i < nchild; ++i) {
            auto ch = item->child(i);
            auto key = ch->key();
            jo.insert(key, genJson(ch));
        }

        return jo;
    } else if (QJsonValue::Array == type) {
        QJsonArray arr;

        for (int i = 0; i < nchild; ++i) {
            auto ch = item->child(i);
            arr.append(genJson(ch));
        }

        return arr;
    } else {
        QJsonValue va(item->value());
        return va;
    }
}
