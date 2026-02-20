
<div id="omgdb_seaepi_generator" class="omgdb_modal fadein">
    <div class="omgdb_modal_box jump">
        <div class="generatorx">
            <form id="omgdbeaepigenerator">
                <input type="hidden" id="dbmvgseitem" name="item" value="1">
                <input type="hidden" id="dbmvgsetotl" name="totl" value="">
                <input type="hidden" id="dbmvgsetmdb" name="tmdb" value="">
                <input type="hidden" id="dbmvgsepare" name="pare" value="">
                <input type="hidden" id="dbmvgsetype" name="type" value="">
                <input type="hidden" id="dbmvgsename" name="name" value="">
                <input type="hidden" id="dbmvgseseas" name="seas" value="">
                <input type="hidden" name="action" value="omegadb_generate_te">
                <div id="omgdbeaepico" class="left loading"></div>
                <div class="right">
                    <input type="submit" id="omgdbeaepbtn" class="button button-primary" value="<?php _z('Import'); ?>" disabled>
                    <a href="#" id="omgdbeaepbtncl" class="button hidden"><?php _z('Cancel'); ?></a>
                    <span id="omgdbeaeptxt" class="text"><?php _z('Loading..'); ?></span>
                    <div id="omgdbeaeprgrs" class="progress">
                        <span></span>
                    </div>
                </div>
            </form>
        </div>
        <div id="dbmvgseconsolelog" class="consolelog"></div>
    </div>
</div>
