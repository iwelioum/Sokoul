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
		<h2><?php echo __('Branding', 'fmovie'); ?></h2>
	</div>


	<div class="admin-box">
		<form action="" method="post" enctype="multipart/form-data">
		<p>
			<label><?php echo __('Minify', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('By activating this, you will see the source of your HTML, inline JavaScript and CSS are now compressed.', 'fmovie'); ?></span>
			<label class="checkbox" for="admin_minify">
				<input type="hidden" value="0" name="admin_minify">
				<input type="checkbox" <?php if (get_option('admin_minify') == 1) { ?> checked="checked" <?php } ?> value="1" id="admin_minify" name="admin_minify">
				<span class="mark"><?php echo __('Enable Minify', 'fmovie'); ?></span>
			</label>
		</p>
        <p>
			<label for="admin_color_style"><?php echo __('Theme color', 'fmovie'); ?></label>
            <span class="helptext"><?php echo __('Choose from 9 colors available.', 'fmovie'); ?></span>
            <select name="admin_color_style" id="admin_color_style">
                <option value="green" <?php if (get_option('admin_color_style') == 'green') { ?> selected="selected" <?php } ?>><?php echo __('green', 'fmovie'); ?></option>
				<option value="blue" <?php if (get_option('admin_color_style') == 'blue') { ?> selected="selected" <?php } ?>><?php echo __('blue', 'fmovie'); ?></option>
                <option value="black" <?php if (get_option('admin_color_style') == 'black') { ?> selected="selected" <?php } ?>><?php echo __('black', 'fmovie'); ?></option>
                <option value="red" <?php if (get_option('admin_color_style') == 'red') { ?> selected="selected" <?php } ?>><?php echo __('red', 'fmovie'); ?></option>
                <option value="purple" <?php if (get_option('admin_color_style') == 'purple') { ?> selected="selected" <?php } ?>><?php echo __('purple', 'fmovie'); ?></option>
                <option value="cherry" <?php if (get_option('admin_color_style') == 'cherry') { ?> selected="selected" <?php } ?>><?php echo __('cherry', 'fmovie'); ?></option>
                <option value="pink" <?php if (get_option('admin_color_style') == 'pink') { ?> selected="selected" <?php } ?>><?php echo __('pink', 'fmovie'); ?></option>
                <option value="yellow" <?php if (get_option('admin_color_style') == 'yellow') { ?> selected="selected" <?php } ?>><?php echo __('yellow', 'fmovie'); ?></option>
                <option value="orange" <?php if (get_option('admin_color_style') == 'orange') { ?> selected="selected" <?php } ?>><?php echo __('orange', 'fmovie'); ?></option>
                <option value="light" <?php if (get_option('admin_color_style') == 'light') { ?> selected="selected" <?php } ?>><?php echo __('light', 'fmovie'); ?></option>
            </select>
		</p>
		<p>
			<label for="admin_deschome"><?php echo __('Home description', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Enter the text for home description', 'fmovie'); ?>
			</span>
			<br>
            <textarea name="admin_deschome" id="admin_deschome"><?php echo stripslashes_deep(get_option('admin_deschome')); ?></textarea>
		</p>
		<p>
			<label for="admin_slogan"><?php echo __('Footer description', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Enter the text for description on the footer left', 'fmovie'); ?>
			<br>
			</span>
			<textarea name="admin_slogan" id="admin_slogan"><?php echo stripslashes_deep(get_option('admin_slogan')); ?></textarea>
		</p>
		<!--<p>
			<label for="admin_description"><?php echo __('Notice', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('A notice message for the home page, very important for SEO, leave blank to deactivate.', 'fmovie'); ?></span>
			<input type="text" name="admin_description" id="admin_description" value="<?php echo stripslashes_deep(get_option('admin_description')); ?>">
		</p>-->

		<!--<p>
			<label for="admin_header_code"><?php echo __('Header Code', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Add code to the head like google analytics code, custom css or more.', 'fmovie'); ?></span>
			<textarea name="admin_header_code" id="admin_header_code"><?php echo stripslashes_deep(get_option('admin_header_code')); ?></textarea>
		</p>-->

			<p><input type="submit" name="update_options" value="<?php echo __('Save settings', 'fmovie'); ?>" class="admin-button admin-button-color-1"></p>
		</form>
	</div>
</div>

<?php get_template_part('admin/footer'); ?>