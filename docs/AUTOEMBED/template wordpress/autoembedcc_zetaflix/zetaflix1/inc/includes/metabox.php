<?php
/* 
* -------------------------------------------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @aopyright: (c) 2023 Zetathemes. All rights reserved
* -------------------------------------------------------------------------------------
*
* @since 1.0.0
*
*/

function zt_post_add_meta_box() {
	add_meta_box(
		'mt_metabox',
		__z('Post meta'),
		'zt_post_html',
		'post',
		'normal',
		'high'
	);
}
add_action('add_meta_boxes', 'zt_post_add_meta_box');
function zt_post_html( $post) { wp_nonce_field('_zt_post_nonce', 'zt_post_nonce'); ?>
<table class="options-table-responsive zt-options-table">
	<tbody>
		<tr id="zt_desc_box">
			<td class="label">
				<label for="zt_post_desc"><?php _z('Short description'); ?></label>
			</td>
			<td class="field">
				<input type="text" name="zt_post_desc" id="zt_post_desc" value="<?php echo zeta_get_postmeta('zt_post_desc'); ?>">
			</td>
		</tr>
		<tr id="zt_dviews_box">
			<td class="label">
				<label for="zt_views_count"><?php _z('Views'); ?></label>
			</td>
			<td class="field">
				<input class="extra-small-text" type="text" name="zt_views_count" id="zt_views_count" value="<?php echo zeta_get_postmeta('zt_views_count'); ?>">
			</td>
		</tr>
	</tbody>
</table>

<?php }
function zt_post_save( $post_id ) {
	if ( defined('DOING_AUTOSAVE') && DOING_AUTOSAVE ) return;
	if ( ! isset( $_POST['zt_post_nonce'] ) || ! wp_verify_nonce( $_POST['zt_post_nonce'], '_zt_post_nonce') ) return;
	if ( ! current_user_can('edit_post', $post_id ) ) return;
/*  Guardar datos */
    if ( isset( $_POST['zt_views_count'] ) ) update_post_meta( $post_id, 'zt_views_count', esc_attr( $_POST['zt_views_count'] ) );
	if ( isset( $_POST['zt_post_desc'] ) ) update_post_meta( $post_id, 'zt_post_desc', esc_attr( $_POST['zt_post_desc'] ) );
}
add_action('save_post', 'zt_post_save'); 
