// NovaOS login splash (KSplash). Plain QtQuick only, works with Qt 6.
import QtQuick 2.15

Rectangle {
    id: root
    color: "#0b1020"
    property int stage

    onStageChanged: {
        if (stage == 2) {
            introAnimation.running = true
        }
    }

    Image {
        anchors.fill: parent
        source: "images/background.png"
        fillMode: Image.PreserveAspectCrop
        asynchronous: false
    }

    Item {
        id: content
        anchors.centerIn: parent
        width: 360
        height: 260
        opacity: 0

        Image {
            id: logo
            anchors.horizontalCenter: parent.horizontalCenter
            source: "images/logo.svg"
            sourceSize.width: 112
            sourceSize.height: 112
        }
        Text {
            id: title
            anchors.top: logo.bottom
            anchors.topMargin: 18
            anchors.horizontalCenter: parent.horizontalCenter
            text: "NovaOS"
            color: "white"
            font.family: "Inter"
            font.pixelSize: 34
            font.weight: Font.DemiBold
        }
        Rectangle {
            id: track
            anchors.top: title.bottom
            anchors.topMargin: 28
            anchors.horizontalCenter: parent.horizontalCenter
            width: 200
            height: 4
            radius: 2
            color: "#33ffffff"
            Rectangle {
                height: parent.height
                radius: 2
                width: parent.width * Math.min(1, root.stage / 6)
                color: "#9d86ff"
                Behavior on width { NumberAnimation { duration: 400; easing.type: Easing.OutCubic } }
            }
        }
    }

    OpacityAnimator {
        id: introAnimation
        running: false
        target: content
        from: 0
        to: 1
        duration: 600
        easing.type: Easing.InOutQuad
    }
}
