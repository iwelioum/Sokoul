<div class="zetaflix_links">
    <div class="dform">
        <fieldset>
            <table>
                <tbody>
                    <tr>
                        <td>
                            <select id='zetaflix_lfield_type' name='zetaflix_lfield_type'>
                                <?php foreach( $this->types() as $type) { echo "<option>{$type}</option>"; } ?>
                            </select>
                        </td>
                        <td>
                            <select id='zetaflix_lfield_lang' name='zetaflix_lfield_lang'>
                                <?php foreach( $this->langs() as $type) { echo "<option>{$type}</option>"; } ?>
                            </select>
                        </td>
                        <td>
                            <select id='zetaflix_lfield_qual' name='zetaflix_lfield_qual'>
                                <?php foreach( $this->resolutions() as $type) { echo "<option>{$type}</option>"; } ?>
                            </select>
                        </td>
                        <td>
                            <input type="text" id="zetaflix_lfield_size" name="zetaflix_lfield_size" placeholder="<?php _z('File size'); ?>"/>
                        </td>
                    </tr>
                </tbody>
            </table>
        </fieldset>
        <fieldset>
            <textarea id="zetaflix_lfield_urls" name="zetaflix_lfield_urls" rows="3" placeholder="<?php _z('Add a link per line'); ?>"></textarea>
        </fieldset>
        <fieldset>
            <a href="#" id="zetaflix_anchor_hideform" class="button button-decundary"><?php _z('Cancel'); ?></a>
            <a href="#" id="zetaflix_anchor_postlinks" class="button button-primary right"><?php _z('Add Links'); ?></a>
        </fieldset>
    </div>
    <div class="dpre">
        <a href="#" id="zetaflix_anchor_showform" class="button button-primary"><?php _z('Add Links'); ?></a>
        <a href="#" id="zetaflix_anchor_reloadllist" class="button button-secundary right" data-id="<?php echo $post->ID; ?>"><?php _z('Reload List'); ?></a>
    </div>
    <table>
        <thead>
            <tr>
                <th><?php _z('Type'); ?></th>
                <th><?php _z('Server'); ?></th>
                <th><?php _z('Language'); ?></th>
                <th><?php _z('Quality'); ?></th>
                <th><?php _z('Size'); ?></th>
                <th><?php _z('Clicks'); ?></th>
                <th><?php _z('User'); ?></th>
                <th><?php _z('Added'); ?></th>
                <th colspan="2"><?php _z('Manage'); ?></th>
            </tr>
        </thead>
        <tbody id="zetalinks_response">
            <?php $this->tablelist_admin($post->ID); ?>
        </tbody>
    </table>
</div>
