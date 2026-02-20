<?php


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
		<h2><?php echo __('Advertising', 'fmovie'); ?></h2>
	</div>
	<div class="admin-box">
		<form action="" method="post" enctype="multipart/form-data">
		<p>
			<label><?php echo __('Activate Fake Buttons?', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Start earning money with our fake buttons.', 'fmovie'); ?></span>
			<label class="radio" for="admin_sponsor_enable"><input type="radio" <?php if (get_option('admin_sponsor') == 1) { ?> checked="checked" <?php } ?> value="1" id="admin_sponsor_enable" name="admin_sponsor"><span class="mark"><?php echo __('Enable', 'fmovie'); ?></span></label>
			<label class="radio" for="admin_sponsor_disable"><input type="radio" <?php if (get_option('admin_sponsor') == 2) { ?> checked="checked" <?php } ?> value="2" id="admin_sponsor_disable" name="admin_sponsor"><span class="mark"><?php echo __('Disable', 'fmovie'); ?></span></label>
		</p>
		<p>
			<label for="admin_sponsor1"><?php echo __('Link for Button 1', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Insert advertiser/referrer link for Button 1', 'fmovie'); ?></span>
			<input type="text" name="admin_sponsor1" id="admin_sponsor1"  value="<?php echo stripslashes_deep(get_option('admin_sponsor1')); ?>">
		</p>
		<p>
			<label for="admin_button1"><?php echo __('Text for Button 1', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify text for Button 1.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Stream in HD', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Stream in HD', 'fmovie'); ?>" name="admin_button1" id="admin_button1"  value="<?php echo stripslashes_deep(get_option('admin_button1')); ?>">
		</p>
		<p>
			<label for="admin_sponsor2"><?php echo __('Link for Button 2', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Insert advertiser/referrer link for Button 2', 'fmovie'); ?></span>
			<input type="text" name="admin_sponsor2" id="admin_sponsor2"  value="<?php echo stripslashes_deep(get_option('admin_sponsor2')); ?>">
		</p>
		<p>
			<label for="admin_button2"><?php echo __('Text for Button 2', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify text for Button 2.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Download in HD', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Download in HD', 'fmovie'); ?>" name="admin_button2" id="admin_button2"  value="<?php echo stripslashes_deep(get_option('admin_button2')); ?>">
		</p>
		<!--<p>
			<label><?php echo __('Activate Fake Play Button?', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Activate Fake button.', 'fmovie'); ?></span>
			<label class="radio" for="admin_adbutton_enable"><input type="radio" <?php if (get_option('admin_adbutton') == 1) { ?> checked="checked" <?php } ?> value="1" id="admin_adbutton_enable" name="admin_adbutton"><span class="mark"><?php echo __('Enable', 'fmovie'); ?></span></label>
			<label class="radio" for="admin_adbutton_disable"><input type="radio" <?php if (get_option('admin_adbutton') == 2) { ?> checked="checked" <?php } ?> value="2" id="admin_adbutton_disable" name="admin_adbutton"><span class="mark"><?php echo __('Disable', 'fmovie'); ?></span></label>
		</p>
		<p>
			<label for="admin_monetize"><?php echo __('Insert fake player link', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Start earning money with our fake play buttons, insert yor publisher advertiser link.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('#', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('#', 'fmovie'); ?>" name="admin_monetize" id="admin_monetize"  value="<?php echo stripslashes_deep(get_option('admin_monetize')); ?>">
		</p>-->
		<p><input type="submit" name="update_options" value="<?php echo __('Save settings', 'fmovie'); ?>" class="admin-button admin-button-color-1"></p>
		</form>
	</div>
</div>

<?php get_template_part('admin/footer'); ?>