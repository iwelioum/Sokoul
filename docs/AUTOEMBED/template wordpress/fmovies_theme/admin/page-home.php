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
		<h2><?php echo __('Home Functions', 'fmovie'); ?></h2>
	</div>
	<div class="admin-box">
		<form action="" method="post" enctype="multipart/form-data">
		<p>
			<label><?php echo __('Login / Logout', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Do you want to enable the "Login / Logout" button on the header?', 'fmovie'); ?><br></span>
			<label class="radio" for="admin_login_enable"><input type="radio" <?php if (get_option('admin_login') == 1) { ?> checked="checked" <?php } ?> value="1" id="admin_login_enable" name="admin_login"><span class="mark"><?php echo __('Yes', 'fmovie'); ?></span></label>
			<label class="radio" for="admin_login_disable"><input type="radio" <?php if (get_option('admin_login') == 2) { ?> checked="checked" <?php } ?> value="2" id="admin_login_disable" name="admin_login"><span class="mark"><?php echo __('No', 'fmovie'); ?></span></label>
		</p>
		<hr>
		<p>
			<label><?php echo __('Slider', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Do you want to enable the "Slider" module on the home page?', 'fmovie'); ?><br></span>
			<span class="helptext"><?php echo __('To add items to the slider and make them viewable, simply assign posts to the "Slider" category.', 'fmovie'); ?><br></span>
			<label class="radio" for="admin_slider_enable"><input type="radio" <?php if (get_option('admin_slider') == 1) { ?> checked="checked" <?php } ?> value="1" id="admin_slider_enable" name="admin_slider"><span class="mark"><?php echo __('Yes', 'fmovie'); ?></span></label>
			<label class="radio" for="admin_slider_disable"><input type="radio" <?php if (get_option('admin_slider') == 2) { ?> checked="checked" <?php } ?> value="2" id="admin_slider_disable" name="admin_slider"><span class="mark"><?php echo __('No', 'fmovie'); ?></span></label>
		</p>
		<hr>
		<p>
			<label><?php echo __('Reccomended', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Do you want to enable the "Reccomended" module on the home page?', 'fmovie'); ?><br></span>
			<span class="helptext"><?php echo __('To add items to the reccomended tabs (Movies & Series) and make them viewable, simply assign posts to the "Reccomended" category.', 'fmovie'); ?><br></span>
			<label class="radio" for="admin_reccomended_enable"><input type="radio" <?php if (get_option('admin_reccomended') == 1) { ?> checked="checked" <?php } ?> value="1" id="admin_reccomended_enable" name="admin_reccomended"><span class="mark"><?php echo __('Yes', 'fmovie'); ?></span></label>
			<label class="radio" for="admin_reccomended_disable"><input type="radio" <?php if (get_option('admin_reccomended') == 2) { ?> checked="checked" <?php } ?> value="2" id="admin_reccomended_disable" name="admin_reccomended"><span class="mark"><?php echo __('No', 'fmovie'); ?></span></label>
		</p>
		<hr>
		<p>
			<label><?php echo __('Latest Movies', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Do you want to enable the "Latest Movies" module on the home page?', 'fmovie'); ?><br></span>
			<label class="radio" for="admin_latest_movies_enable"><input type="radio" <?php if (get_option('admin_latest_movies') == 1) { ?> checked="checked" <?php } ?> value="1" id="admin_latest_movies_enable" name="admin_latest_movies"><span class="mark"><?php echo __('Yes', 'fmovie'); ?></span></label>
			<label class="radio" for="admin_latest_movies_disable"><input type="radio" <?php if (get_option('admin_latest_movies') == 2) { ?> checked="checked" <?php } ?> value="2" id="admin_latest_movies_disable" name="admin_latest_movies"><span class="mark"><?php echo __('No', 'fmovie'); ?></span></label>
		</p>
		<hr>
		<p>
			<label><?php echo __('Latest TV series', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Do you want to enable the "Latest TV Series" module on the home page?', 'fmovie'); ?><br></span>
			<label class="radio" for="admin_latest_series_enable"><input type="radio" <?php if (get_option('admin_latest_series') == 1) { ?> checked="checked" <?php } ?> value="1" id="admin_latest_series_enable" name="admin_latest_series"><span class="mark"><?php echo __('Yes', 'fmovie'); ?></span></label>
			<label class="radio" for="admin_latest_series_disable"><input type="radio" <?php if (get_option('admin_latest_series') == 2) { ?> checked="checked" <?php } ?> value="2" id="admin_latest_series_disable" name="admin_latest_series"><span class="mark"><?php echo __('No', 'fmovie'); ?></span></label>
		</p>
		<hr>
		<p>
			<label><?php echo __('Genre', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Do you want to enable the "Genre" dropdown menù on top?', 'fmovie'); ?><br></span>
			<label class="radio" for="admin_genre_link_enable"><input type="radio" <?php if (get_option('admin_genre_link') == 1) { ?> checked="checked" <?php } ?> value="1" id="admin_genre_link_enable" name="admin_genre_link"><span class="mark"><?php echo __('Yes', 'fmovie'); ?></span></label>
			<label class="radio" for="admin_genre_link_disable"><input type="radio" <?php if (get_option('admin_genre_link') == 2) { ?> checked="checked" <?php } ?> value="2" id="admin_genre_link_disable" name="admin_genre_link"><span class="mark"><?php echo __('No', 'fmovie'); ?></span></label>
		</p>
		<hr>
		<p>
			<label><?php echo __('Country', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Do you want to enable the "Country" link on top menù?', 'fmovie'); ?><br></span>
			<label class="radio" for="admin_country_link_enable"><input type="radio" <?php if (get_option('admin_country_link') == 1) { ?> checked="checked" <?php } ?> value="1" id="admin_country_link_enable" name="admin_country_link"><span class="mark"><?php echo __('Yes', 'fmovie'); ?></span></label>
			<label class="radio" for="admin_country_link_disable"><input type="radio" <?php if (get_option('admin_country_link') == 2) { ?> checked="checked" <?php } ?> value="2" id="admin_country_link_disable" name="admin_country_link"><span class="mark"><?php echo __('No', 'fmovie'); ?></span></label>
		</p>
		<hr>
		<p>
			<label><?php echo __('Top IMDB', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Do you want to enable the "Top IMDB" page and also link of this page on top menù?', 'fmovie'); ?><br></span>
			<label class="radio" for="admin_top_imdb_enable"><input type="radio" <?php if (get_option('admin_top_imdb') == 1) { ?> checked="checked" <?php } ?> value="1" id="admin_top_imdb_enable" name="admin_top_imdb"><span class="mark"><?php echo __('Yes', 'fmovie'); ?></span></label>
			<label class="radio" for="admin_top_imdb_disable"><input type="radio" <?php if (get_option('admin_top_imdb') == 2) { ?> checked="checked" <?php } ?> value="2" id="admin_top_imdb_disable" name="admin_top_imdb"><span class="mark"><?php echo __('No', 'fmovie'); ?></span></label>
		</p>
		<hr>
		<p>
			<label><?php echo __('Favorites', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Do you want to enable the "Favorites" link on top menù?', 'fmovie'); ?><br></span>
			<label class="radio" for="admin_favorites_link_enable"><input type="radio" <?php if (get_option('admin_favorites_link') == 1) { ?> checked="checked" <?php } ?> value="1" id="admin_favorites_link_enable" name="admin_favorites_link"><span class="mark"><?php echo __('Yes', 'fmovie'); ?></span></label>
			<label class="radio" for="admin_favorites_link_disable"><input type="radio" <?php if (get_option('admin_favorites_link') == 2) { ?> checked="checked" <?php } ?> value="2" id="admin_favorites_link_disable" name="admin_favorites_link"><span class="mark"><?php echo __('No', 'fmovie'); ?></span></label>
		</p>
		<hr>
		<p>
			<label><?php echo __('Related Posts', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Do you want to enable the "Related Posts" module on single Movie/Series?', 'fmovie'); ?><br></span>
			<label class="radio" for="admin_related_post_enable"><input type="radio" <?php if (get_option('admin_related_post') == 1) { ?> checked="checked" <?php } ?> value="1" id="admin_related_post_enable" name="admin_related_post"><span class="mark"><?php echo __('Yes', 'fmovie'); ?></span></label>
			<label class="radio" for="admin_related_post_disable"><input type="radio" <?php if (get_option('admin_related_post') == 2) { ?> checked="checked" <?php } ?> value="2" id="admin_related_post_disable" name="admin_related_post"><span class="mark"><?php echo __('No', 'fmovie'); ?></span></label>
		</p>

		<hr>
		<p><input type="submit" name="update_options" value="<?php echo __('Save settings', 'fmovie'); ?>" class="admin-button admin-button-color-1"></p>
		</form>
	</div>
</div>
<?php get_template_part('admin/footer'); ?>