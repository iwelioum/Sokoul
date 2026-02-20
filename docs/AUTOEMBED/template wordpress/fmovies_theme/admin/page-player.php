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
		<h2><?php echo __('Player', 'fmovie'); ?></h2>
	</div>
	<div class="admin-box">
		<form action="" method="post" enctype="multipart/form-data">
		<p>
	

<label><?php echo __('Activate Premium API?', 'fmovie'); ?></label>
<span class="helptext"><?php echo __('By activating this option you will be able to use the', 'fmovie'); ?> <a href="<?php echo esc_url( __( wp_get_theme()->get( 'AuthorURI' )) ); ?>" target="_blank"><strong><?php printf( __( 'premium API %s', 'fmovie' ),''); ?></strong></a><br></span>
<label class="radio" for="admin_premium_enable">
    <input type="radio" <?php if (get_option('admin_premium') == 1 || get_option('admin_premium') === false) { ?> checked="checked" <?php } ?> value="1" id="admin_premium_enable" name="admin_premium">
    <span class="mark"><?php echo __('Yes', 'fmovie'); ?></span>
</label>
<label class="radio" for="admin_premium_disable">
    <input type="radio" <?php if (get_option('admin_premium') == 2) { ?> checked="checked" <?php } ?> value="2" id="admin_premium_disable" name="admin_premium">
    <span class="mark"><?php echo __('No', 'fmovie'); ?></span>
</label>

		</p>
		<p>
			<label for="admin_server_0_text"><?php echo __('Enter text for Server Premium', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Enter text for Premium.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Premium', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Premium', 'fmovie'); ?>" name="admin_server_0_text" id="admin_server_0_text"  value="<?php echo stripslashes_deep(get_option('admin_server_0_text')); ?>">
		</p>
		<p>
			<label for="admin_server_1_text"><?php echo __('Enter text for Server 2embed', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Enter text for 2embed.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('2embed', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('2embed', 'fmovie'); ?>" name="admin_server_1_text" id="admin_server_1_text"  value="<?php echo stripslashes_deep(get_option('admin_server_1_text')); ?>">
		</p>
		<p>
			<label><?php echo __('Activate Server Multi?', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('By activating this option you will be able to use the Server Multi', 'fmovie'); ?><br></span>
			<label class="radio" for="admin_server_2_enable"><input type="radio" <?php if (get_option('admin_server_2') == 1) { ?> checked="checked" <?php } ?> value="1" id="admin_server_2_enable" name="admin_server_2"><span class="mark"><?php echo __('Yes', 'fmovie'); ?></span></label>
			<label class="radio" for="admin_server_2_disable"><input type="radio" <?php if (get_option('admin_server_2') == 2) { ?> checked="checked" <?php } ?> value="2" id="admin_server_2_disable" name="admin_server_2"><span class="mark"><?php echo __('No', 'fmovie'); ?></span></label>
		</p>
		<p>
			<label for="admin_server_2_text"><?php echo __('Enter text for Server Multi', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Enter text for Multi.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Multi', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Multi', 'fmovie'); ?>" name="admin_server_2_text" id="admin_server_2_text"  value="<?php echo stripslashes_deep(get_option('admin_server_2_text')); ?>">
		</p>
		<p>
			<label><?php echo __('Activate Server Vidsrc?', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('By activating this option you will be able to use the Server Vidsrc', 'fmovie'); ?><br></span>
			<label class="radio" for="admin_server_3_enable"><input type="radio" <?php if (get_option('admin_server_3') == 1) { ?> checked="checked" <?php } ?> value="1" id="admin_server_3_enable" name="admin_server_3"><span class="mark"><?php echo __('Yes', 'fmovie'); ?></span></label>
			<label class="radio" for="admin_server_3_disable"><input type="radio" <?php if (get_option('admin_server_3') == 2) { ?> checked="checked" <?php } ?> value="2" id="admin_server_3_disable" name="admin_server_3"><span class="mark"><?php echo __('No', 'fmovie'); ?></span></label>
		</p>
		<p>
			<label for="admin_server_3_text"><?php echo __('Enter text for Server Vidsrc', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Enter text for Vidsrc.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Vidsrc', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Vidsrc', 'fmovie'); ?>" name="admin_server_3_text" id="admin_server_3_text"  value="<?php echo stripslashes_deep(get_option('admin_server_3_text')); ?>">
		</p>
		<!--<p>
			<label><?php echo __('Fix the problem with the TV series player?', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Enabling this option will try to solve the TV player url rewrite problem that can happen in some hosts', 'fmovie'); ?></span>
			<label class="radio" for="admin_rewrite_enable"><input type="radio" <?php if (get_option('admin_rewrite') == 1) { ?> checked="checked" <?php } ?> value="1" id="admin_rewrite_enable" name="admin_rewrite"><span class="mark"><?php echo __('Yes', 'fmovie'); ?></span></label>
			<label class="radio" for="admin_rewrite_disable"><input type="radio" <?php if (get_option('admin_rewrite') == 2) { ?> checked="checked" <?php } ?> value="2" id="admin_rewrite_disable" name="admin_rewrite"><span class="mark"><?php echo __('No', 'fmovie'); ?></span></label>
		</p>-->
		<p><input type="submit" name="update_options" value="<?php echo __('Save settings', 'fmovie'); ?>" class="admin-button admin-button-color-1"></p>
		</form>
	</div>
</div>
<script>
    jQuery(document).ready(function($) {
        $('input[name=admin_premium]').click(function(e) {
            if ($(this).val() == 1) {
                // Allow the default behavior for "Yes" (value 1)
            } else {
                e.preventDefault();
                alert('You cannot activate this feature!');
            }
        });
    });
</script>

<?php get_template_part('admin/footer'); ?>