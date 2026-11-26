// NovaOS login splash (KSplash), glass design. Plain QtQuick only (no effects
// modules): the "glass" card shows a blurred copy of the background exactly at
// its own position, so it looks like frosted glass on every graphics driver.
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
        id: wallpaper
        anchors.fill: parent
        source: "images/background.png"
        fillMode: Image.PreserveAspectCrop
        asynchronous: false
    }

    Item {
        id: card
        anchors.centerIn: parent
        width: 380
        height: 300
        opacity: 0
        scale: 0.96
        clip: true

        // frosted glass: the blurred background, moved so that it lines up
        Image {
            x: -card.x
            y: -card.y
            width: root.width
            height: root.height
            source: "images/background-blur.jpg"
            fillMode: Image.PreserveAspectCrop
        }
        Rectangle {
            anchors.fill: parent
            radius: 30
            color: "#2effffff"
            border.color: "#55ffffff"
            border.width: 1
        }
        // soft light at the top edge of the glass
        Rectangle {
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.top: parent.top
            height: parent.height / 2
            radius: 30
            gradient: Gradient {
                GradientStop { position: 0.0; color: "#22ffffff" }
                GradientStop { position: 1.0; color: "#00ffffff" }
            }
        }

        Image {
            id: logo
            anchors.horizontalCenter: parent.horizontalCenter
            anchors.top: parent.top
            anchors.topMargin: 44
            source: "images/logo.svg"
            sourceSize.width: 96
            sourceSize.height: 96
        }
        Text {
            id: title
            anchors.top: logo.bottom
            anchors.topMargin: 16
            anchors.horizontalCenter: parent.horizontalCenter
            text: "NovaOS"
            color: "white"
            font.family: "Inter"
            font.pixelSize: 34
            font.weight: Font.DemiBold
        }
        Text {
            id: hello
            anchors.top: title.bottom
            anchors.topMargin: 4
            anchors.horizontalCenter: parent.horizontalCenter
            text: Qt.locale().name.indexOf("de") === 0 ? "Willkommen" : "Welcome"
            color: "#ccffffff"
            font.family: "Inter"
            font.pixelSize: 15
        }
        Rectangle {
            id: track
            anchors.top: hello.bottom
            anchors.topMargin: 26
            anchors.horizontalCenter: parent.horizontalCenter
            width: 220
            height: 6
            radius: 3
            color: "#33ffffff"
            Rectangle {
                height: parent.height
                radius: 3
                width: parent.width * Math.min(1, root.stage / 6)
                gradient: Gradient {
                    orientation: Gradient.Horizontal
                    GradientStop { position: 0.0; color: "#9d86ff" }
                    GradientStop { position: 1.0; color: "#6aa1ff" }
                }
                Behavior on width { NumberAnimation { duration: 400; easing.type: Easing.OutCubic } }
            }
        }
    }

    ParallelAnimation {
        id: introAnimation
        running: false
        NumberAnimation { target: card; property: "opacity"; from: 0; to: 1; duration: 600; easing.type: Easing.InOutQuad }
        NumberAnimation { target: card; property: "scale"; from: 0.96; to: 1; duration: 600; easing.type: Easing.OutCubic }
    }
}
