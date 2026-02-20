<?php
/*
* ----------------------------------------------------
* @author: fr0zen
* @author URI: https://fr0zen.sellix.io
* @copyright: (c) 2022 Vincenzo Piromalli. All rights reserved
* ----------------------------------------------------
* @since 3.8.7
* 20 May 2022
*/

/* Exit if accessed directly */
if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

get_template_part('admin/header'); ?>

<div class="admin-column">
	<?php if (isset($_POST["update_options"])) { ?>
		<?php
			foreach ($_POST as $key => $value) {
                if ($key != 'update_options') {
					update_option($key, esc_html($value));
				}
            }
		?>
		<div class="admin-box admin-updated"><?php echo __('Settings saved', 'fmovie'); ?></div>		
	<?php } ?>
	<div class="admin-box">
		<h2><?php echo __('Social', 'fmovie'); ?></h2>
	</div>
	<div class="admin-box">
		<form action="" method="post" enctype="multipart/form-data">
		<p>
			<label><?php echo __('Social icons in Header?', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Add links to your social profiles on to right header bar.', 'fmovie'); ?></span>
			<label class="radio" for="admin_header_soc_icons_enable"><input type="radio" <?php if (get_option('admin_header_soc_icons') == 1) { ?> checked="checked" <?php } ?> value="1" id="admin_header_soc_icons_enable" name="admin_header_soc_icons"><span class="mark"><?php echo __('Enable', 'fmovie'); ?></span></label>
			<label class="radio" for="admin_header_soc_icons_disable"><input type="radio" <?php if (get_option('admin_header_soc_icons') == 2) { ?> checked="checked" <?php } ?> value="2" id="admin_header_soc_icons_disable" name="admin_header_soc_icons"><span class="mark"><?php echo __('Disable', 'fmovie'); ?></span></label>
		</p>
		<p>
			<label for="admin_url_facebook"><?php echo __('Facebook', 'fmovie'); ?></label>
			<input type="text" name="admin_url_facebook" id="admin_url_facebook" value="<?php echo stripslashes_deep(get_option('admin_url_facebook')); ?>">
		</p>
		<p>
			<label for="admin_url_twitter"><?php echo __('Twitter', 'fmovie'); ?></label>
			<input type="text" name="admin_url_twitter" id="admin_url_twitter" value="<?php echo stripslashes_deep(get_option('admin_url_twitter')); ?>">
		</p>
		<p>
			<label for="admin_url_instagram"><?php echo __('Instagram', 'fmovie'); ?></label>
			<input type="text" name="admin_url_instagram" id="admin_url_instagram" value="<?php echo stripslashes_deep(get_option('admin_url_instagram')); ?>">
		</p>
		<p><input type="submit" name="update_options" value="<?php echo __('Save settings', 'fmovie'); ?>" class="admin-button admin-button-color-1"></p>
		</form>
	</div>
</div>

<?php get_template_part('admin/footer'); ?>