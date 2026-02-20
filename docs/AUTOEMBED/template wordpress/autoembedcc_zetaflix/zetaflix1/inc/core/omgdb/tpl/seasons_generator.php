<div class="omegadb-tvshow-content-generator">
    <button id="omegadb-generator-seprepa" class="button button-primary"><?php _z('Get data'); ?></button>
    <button id="omegadb-generator-seasons" class="button button-secundary" disabled><?php echo $sbtn; ?></button>
    <span id="omegadb-json-response"></span>
    <span id="omegadb-loader" class="spinner"></span>
    <input type="hidden" id="postparent" value="<?php echo $post->ID; ?>">
    <input type="hidden" id="tmdbsename" value="">
    <input type="hidden" id="tmdbseasos" value="">
    <input type="hidden" id="tmdbseason" value="1">
</div>
<div class="omegadb-progress-box">
    <div id="omegadb-progress" class="progress"></div>
</div>
