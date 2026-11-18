"""Asynchronous image loading (icons, screenshots) with an on-disk cache."""

from __future__ import annotations

import os

from PyQt6.QtCore import QObject, QStandardPaths, QUrl, pyqtSignal
from PyQt6.QtGui import QPixmap
from PyQt6.QtNetwork import (
    QNetworkAccessManager,
    QNetworkDiskCache,
    QNetworkProxyFactory,
    QNetworkReply,
    QNetworkRequest,
)


class ImageLoader(QObject):
    loaded = pyqtSignal(str, QPixmap)   # url, pixmap

    def __init__(self, parent: QObject | None = None):
        super().__init__(parent)
        QNetworkProxyFactory.setUseSystemConfiguration(True)
        self._nam = QNetworkAccessManager(self)
        cache = QNetworkDiskCache(self)
        base = QStandardPaths.writableLocation(QStandardPaths.StandardLocation.CacheLocation)
        cache.setCacheDirectory(os.path.join(base or os.path.expanduser("~/.cache/nova-store"), "images"))
        cache.setMaximumCacheSize(300 * 1024 * 1024)
        self._nam.setCache(cache)
        self._mem: dict[str, QPixmap] = {}
        self._pending: dict[str, list] = {}

    def get(self, url: str, callback) -> None:
        """Call ``callback(QPixmap)`` once the image is available."""
        if not url:
            return
        if url in self._mem:
            callback(self._mem[url])
            return
        if url.startswith("/") or url.startswith("file:"):
            pix = QPixmap(url.removeprefix("file://"))
            self._mem[url] = pix
            callback(pix)
            return
        if url in self._pending:
            self._pending[url].append(callback)
            return
        self._pending[url] = [callback]
        req = QNetworkRequest(QUrl(url))
        req.setAttribute(QNetworkRequest.Attribute.CacheLoadControlAttribute,
                         QNetworkRequest.CacheLoadControl.PreferCache)
        req.setHeader(QNetworkRequest.KnownHeaders.UserAgentHeader, "NovaStore/0.1")
        reply = self._nam.get(req)
        reply.finished.connect(lambda r=reply, u=url: self._finished(u, r))

    def _finished(self, url: str, reply: QNetworkReply) -> None:
        pix = QPixmap()
        if reply.error() == QNetworkReply.NetworkError.NoError:
            pix.loadFromData(bytes(reply.readAll()))
        reply.deleteLater()
        if not pix.isNull():
            self._mem[url] = pix
        for cb in self._pending.pop(url, []):
            try:
                cb(pix)
            except RuntimeError:
                pass  # receiving widget was deleted meanwhile
        self.loaded.emit(url, pix)
