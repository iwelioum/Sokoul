<table class="options-table-responsive zt-options-table">
    <tbody>
        <tr id="parent_row">
            <td class="label">
                <label><?php _z('Parent'); ?></label>
            </td>
            <td class="field">
                <a href="<?php echo admin_url('post.php?post='.wp_get_post_parent_id($post->ID).'&action=edit'); ?>"><strong><?php echo get_the_title( wp_get_post_parent_id($post->ID) ); ?></strong></a>
            </td>
        </tr>
        <tr id="zetal_type_row">
            <td class="label">
                <label><?php _z('Type'); ?></label>
            </td>
            <td class="field">
                <select name="_zetal_type" id="zetal_type">
                    <?php foreach( $this->types() as $type ) { echo '<option '.selected( get_post_meta($post->ID, $this->metatype, true), $type, false).'>'.$type.'</option>'; } ?>
                </select>
            </td>
        </tr>
        <tr id="zetal_url_row">
            <td class="label">
                <label><?php _z('URL Link'); ?></label>
            </td>
            <td class="field">
                <input class="regular-text" type="text" name="_zetal_url" id="zetal_url" value="<?php echo get_post_meta($post->ID, $this->metaurl, true); ?>">
            </td>
        </tr>
        <tr id="zetal_size_row">
            <td class="label">
                <label><?php _z('File size'); ?></label>
            </td>
            <td class="field">
                <input class="regular-text" type="text" name="_zetal_size" id="zetal_size" value="<?php echo get_post_meta($post->ID, $this->metasize, true); ?>">
            </td>
        </tr>
        <tr id="zetal_lang_row">
            <td class="label">
                <label><?php _z('Language'); ?></label>
            </td>
            <td class="field">
                <select name="_zetal_lang" id="zetal_lang">
                    <?php foreach( $this->langs() as $lang ) { echo '<option '.selected( get_post_meta($post->ID, $this->metalang, true), $lang, false).'>'.$lang.'</option>'; } ?>
                </select>
            </td>
        </tr>
        <tr id="zetal_quality_row">
            <td class="label">
                <label><?php _z('Quality'); ?></label>
            </td>
            <td class="field">
                <select name="_zetal_quality" id="zetal_quality">
                    <?php foreach( $this->resolutions() as $resolution ) { echo '<option '.selected( get_post_meta($post->ID, $this->metaquality, true), $resolution, false).'>'.$resolution.'</option>'; } ?>
                </select>
            </td>
        </tr>
    </tbody>
</table>
