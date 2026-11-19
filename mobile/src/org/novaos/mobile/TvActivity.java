package org.novaos.mobile;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.novaos.tv.Channel;

import java.util.List;

/** TV on the phone: all internet channels as coloured tiles; a tap starts the player. */
public class TvActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout content = Page.setup(this, R.string.tv_title, getString(R.string.tv_sub));
        List<Channel> channels = Channels.get(this);
        int cols = Greeting.columns(Ui.widthDp(this) - 40, 160, 2, 6);
        int gap = Ui.dp(this, 10);
        LinearLayout row = null;
        for (int i = 0; i < channels.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 92));
                rl.topMargin = gap;
                content.addView(row, rl);
            }
            Channel c = channels.get(i);
            View tile = Ui.channelTile(this, c.number, c.name, c.color);
            final int pos = i;
            tile.setOnClickListener(v -> startActivity(new Intent(this, PlayerActivity.class)
                    .putExtra(PlayerActivity.EXTRA_INDEX, pos)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
            if (i % cols != 0) lp.leftMargin = gap;
            row.addView(tile, lp);
        }
        int rest = (cols - channels.size() % cols) % cols;
        for (int i = 0; i < rest && row != null; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1);
            lp.leftMargin = gap;
            row.addView(new View(this), lp);
        }
    }
}
