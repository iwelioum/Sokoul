<?php
$prid = wp_get_post_parent_id($post_id);
$ptit = get_the_title($prid);
$pprl = get_the_permalink($prid);
?>
<tr class="link-row-edit">

<td colspan="8">
<form id="zeta_link_front_editor">
 <h3><?php _z('Edit Link'); ?></h3>
 <div class="edit-field">
<span class="edit-field-title"><?php _z('Type:');?></span>
          <select name="type">
            <?php foreach( $this->types() as $type ) { echo '<option '.selected( get_post_meta($post_id, '_zetal_type', true), $type, false).'>'.$type.'</option>'; } ?>
            </select>
</div>
 <div class="edit-field">
<span class="edit-field-title"><?php _z('Source:');?></span>
<input name="murl" type="text" value="<?php echo get_post_meta( $post_id, '_zetal_url', true ); ?>">
</div>
 <div class="edit-field">
<span class="edit-field-title"><?php _z('Language:');?></span>
            <select name="lang">
            <?php foreach( $this->langs() as $lang ) { echo '<option '.selected( get_post_meta($post_id, '_zetal_lang', true), $lang, false).'>'.$lang.'</option>'; } ?>
            </select>
</div>
 <div class="edit-field">
<span class="edit-field-title"><?php _z('Quality:');?></span>
            <select name="qual">
            <?php foreach( $this->resolutions() as $resolution ) { echo '<option '.selected( get_post_meta($post_id, '_zetal_quality', true), $resolution, false).'>'.$resolution.'</option>'; } ?>
            </select>
</div>
 <div class="edit-field">
<span class="edit-field-title"><?php _z('Size:');?></span>
            <input type="text" name="size" id="size" value="<?php echo get_post_meta( $post_id, '_zetal_size', true ); ?>" placeholder="<?php _z('File size (optional)'); ?>">
</div>
 <div class="edit-field">
<input type="submit" value="<?php _z('Save'); ?>" title="Save Data">

        <a data-eid="<?php echo $post_id;?>" data-etype="user" id="cerrar_form_edit_link" title="Close">
            <i class="fas fa-times"></i>
        </a>
		</div>
            <input type="hidden" id="ptid" name="ptid" value="<?php echo $post_id; ?>">
            <input type="hidden" id="nonc" name="nonc" value="<?php echo wp_create_nonce('zetalinksaveformeditor_'.$post_id); ?>">
            <input type="hidden" name="action" value="zetasaveformeditor_user">
</form>		
		</td>
            

</tr>