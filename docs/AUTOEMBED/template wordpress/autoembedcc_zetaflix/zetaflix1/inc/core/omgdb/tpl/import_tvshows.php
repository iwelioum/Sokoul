

<div id="dbmvoies-importer-html">
    <div class="omegadb-quick-importer">
        <form id="omegadb-importer">
            <div class="box">
                <span id="omegadb-loader" class="spinner"></span>
                <input type="text" name="ptmdb" placeholder="<?php _z('Paste URL of TMDb'); ?>" id="omegadb-inp-tmdb" required>
                <input type="submit" value="<?php _z('Import'); ?>" class="button button-primary" id="omegadb-btn-importer">
                <input type="hidden" value="tvshow" name="ptype">
                <input type="hidden" value="omegadb_insert_tmdb" name="action">
            </div>
        </form>
    </div>
</div>
