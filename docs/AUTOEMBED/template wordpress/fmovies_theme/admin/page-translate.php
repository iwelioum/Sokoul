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
		<h2><?php echo __('Translate', 'fmovie'); ?></h2>
	</div>
	<div class="admin-box">
		<form action="" method="post" enctype="multipart/form-data">
		<p>
			<label for="admin_textlatest"><?php echo __('Enter text for Latest', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify Latest', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Latest', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Latest', 'fmovie'); ?>" name="admin_textlatest" id="admin_textlatest" value="<?php echo stripslashes_deep(get_option('admin_textlatest')); ?>">
		</p>
		<p>
			<label for="admin_textviewall"><?php echo __('Enter text for View all', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify View all', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('View all', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('View all', 'fmovie'); ?>" name="admin_textviewall" id="admin_textviewall" value="<?php echo stripslashes_deep(get_option('admin_textviewall')); ?>">
		</p>
		<p>
			<label for="admin_recommended"><?php echo __('Enter text for Recommended', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Enter text for Recommended.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Recommended', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Recommended', 'fmovie'); ?>" name="admin_recommended" id="admin_recommended"  value="<?php echo stripslashes_deep(get_option('admin_recommended')); ?>">
		</p>
		<p>
			<label for="admin_trending"><?php echo __('Enter text for Trending', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Enter text for Trending.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Trending', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Trending', 'fmovie'); ?>" name="admin_trending" id="admin_trending"  value="<?php echo stripslashes_deep(get_option('admin_trending')); ?>">
		</p>
		<p>
			<label for="admin_txtmovies"><?php echo __('Enter text for Movies', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify Movies on sidebar.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Movies', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Movies', 'fmovie'); ?>" name="admin_txtmovies" id="admin_txtmovies" value="<?php echo stripslashes_deep(get_option('admin_txtmovies')); ?>">
		</p>
		<p>
			<label for="admin_tvseries"><?php echo __('Enter text for TV Series', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify TV Series on sidebar.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('TV Series', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('TV Series', 'fmovie'); ?>" name="admin_tvseries" id="admin_tvseries" value="<?php echo stripslashes_deep(get_option('admin_tvseries')); ?>">
		</p>
		<!--<p>
			<label for="admin_intheaters"><?php echo __('Enter text for In Theaters', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Enter text for In Theaters category on sidebar.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('In Theaters', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('In Theaters', 'fmovie'); ?>" name="admin_intheaters" id="admin_intheaters" value="<?php echo stripslashes_deep(get_option('admin_intheaters')); ?>">
		</p>-->
		<p>
			<label for="admin_top"><?php echo __('Enter text for Top IMDb', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify Top IMDb page.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Top IMDb', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Top IMDb', 'fmovie'); ?>" name="admin_top" id="admin_top" value="<?php echo stripslashes_deep(get_option('admin_top')); ?>">
		</p>
		<!--<p>
			<label for="admin_random"><?php echo __('Enter text for Random', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify Random page on sidebar.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Random', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Random', 'fmovie'); ?>" name="admin_random" id="admin_random" value="<?php echo stripslashes_deep(get_option('admin_random')); ?>">
		</p>-->
		<p>
			<label for="admin_genre"><?php echo __('Enter text for Genre', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Enter text for Genre.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Genre', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Genre', 'fmovie'); ?>" name="admin_genre" id="admin_genre" value="<?php echo stripslashes_deep(get_option('admin_genre')); ?>">
		</p>
		<p>
			<label for="admin_txtquality"><?php echo __('Enter text for Quality', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify Quality.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Quality.', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Quality.', 'fmovie'); ?>" name="admin_txtquality" id="admin_txtquality"  value="<?php echo stripslashes_deep(get_option('admin_txtquality')); ?>">
		</p>
		<p>
			<label for="admin_year"><?php echo __('Enter text for Year', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify Year.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Year', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Year', 'fmovie'); ?>" name="admin_year" id="admin_year" value="<?php echo stripslashes_deep(get_option('admin_year')); ?>">
		</p>
		<p>
			<label for="admin_country"><?php echo __('Enter text for Country', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Enter text for Country.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Country', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Country', 'fmovie'); ?>" name="admin_country" id="admin_country"  value="<?php echo stripslashes_deep(get_option('admin_country')); ?>">
		</p>
		<p>
			<label for="admin_search"><?php echo __('Enter text for Search', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Enter text for search on menù dropdown.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Search...', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Search...', 'fmovie'); ?>" name="admin_search" id="admin_search"  value="<?php echo stripslashes_deep(get_option('admin_search')); ?>">
		</p>
		<p>
			<label for="admin_network"><?php echo __('Enter text for Network', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Enter text for network on tv show page.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Network', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Network', 'fmovie'); ?>" name="admin_network" id="admin_network"  value="<?php echo stripslashes_deep(get_option('admin_network')); ?>">
		</p>
		<p>
			<label for="admin_creator"><?php echo __('Enter text for Creator', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Enter text for Creator on tv show page.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Creator', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Creator', 'fmovie'); ?>" name="admin_creator" id="admin_creator"  value="<?php echo stripslashes_deep(get_option('admin_creator')); ?>">
		</p>
		<p>
			<label for="admin_stars"><?php echo __('Enter text for Stars', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Enter text for stars on tv show page.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Stars', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Stars', 'fmovie'); ?>" name="admin_stars" id="admin_stars"  value="<?php echo stripslashes_deep(get_option('admin_stars')); ?>">
		</p>
		<p>
			<label for="admin_season"><?php echo __('Enter text for Season', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify Season on tv show page.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Season', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Season', 'fmovie'); ?>" name="admin_season" id="admin_season" value="<?php echo stripslashes_deep(get_option('admin_season')); ?>">
		</p>
		<p>
			<label for="admin_seasons"><?php echo __('Enter text for Seasons', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify Seasons on tv show page.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Seasons', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Seasons', 'fmovie'); ?>" name="admin_seasons" id="admin_seasons" value="<?php echo stripslashes_deep(get_option('admin_seasons')); ?>">
		</p>
		<p>
			<label for="admin_episode"><?php echo __('Enter text for Episode', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify Episode on tv show page.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Episode', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Episode', 'fmovie'); ?>" name="admin_episode" id="admin_episode" value="<?php echo stripslashes_deep(get_option('admin_episode')); ?>">
		</p>
		<p>
			<label for="admin_episodes"><?php echo __('Enter text for Episodes', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify Episodes on tv show page.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Episodes', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Episodes', 'fmovie'); ?>" name="admin_episodes" id="admin_episodes" value="<?php echo stripslashes_deep(get_option('admin_episodes')); ?>">
		</p>
		<p>
			<label for="admin_director"><?php echo __('Enter text for Director', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify Director on movies.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Director', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Director', 'fmovie'); ?>" name="admin_director" id="admin_director"  value="<?php echo stripslashes_deep(get_option('admin_director')); ?>">
		</p>
		<!--<p>
			<label for="admin_play"><?php echo __('Enter text for Play', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify Play button.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Play', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Play', 'fmovie'); ?>" name="admin_play" id="admin_play"  value="<?php echo stripslashes_deep(get_option('admin_play')); ?>">
		</p>-->
		<!--<p>
			<label for="admin_share"><?php echo __('Enter text for Report', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify Report button.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Report', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Report', 'fmovie'); ?>" name="admin_share" id="admin_share"  value="<?php echo stripslashes_deep(get_option('admin_share')); ?>">
		</p>-->
		<p>
			<label for="admin_trailer"><?php echo __('Enter text for Trailer', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify Trailer.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Trailer', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Trailer', 'fmovie'); ?>" name="admin_trailer" id="admin_trailer"  value="<?php echo stripslashes_deep(get_option('admin_trailer')); ?>">
		</p>
		<p>
			<label for="admin_streaming"><?php echo __('Enter text for Toggle light', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify Toggle light button.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Toggle light', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Toggle light', 'fmovie'); ?>" name="admin_streaming" id="admin_streaming"  value="<?php echo stripslashes_deep(get_option('admin_streaming')); ?>">
		</p>
		<!--<p>
			<label for="admin_download"><?php echo __('Enter text for Download', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify download button.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Download', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Download', 'fmovie'); ?>" name="admin_download" id="admin_download" value="<?php echo stripslashes_deep(get_option('admin_download')); ?>">
		</p>-->
		<p>
			<label for="admin_watch"><?php echo __('Enter text for Watch Now', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify Watch Now Button.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Watch Now', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Watch Now', 'fmovie'); ?>" name="admin_watch" id="admin_watch" value="<?php echo stripslashes_deep(get_option('admin_watch')); ?>">
		</p>
		<!--<p>
			<label for="admin_advertise"><?php echo __('Enter text for Advertise', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify Advertise Here button, you can use this text as a fake player button to monetize your site.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Advertise Here', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Advertise Here', 'fmovie'); ?>" name="admin_advertise" id="admin_advertise"  value="<?php echo stripslashes_deep(get_option('admin_advertise')); ?>">
		</p>-->
		<!--<p>
			<label for="admin_textmultiserver"><?php echo __('Enter text for Multi Server', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify Multi Server module.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Multi Server', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Multi Server', 'fmovie'); ?>" name="admin_textmultiserver" id="admin_textmultiserver"  value="<?php echo stripslashes_deep(get_option('admin_textmultiserver')); ?>">
		</p>-->
		<p>
			<label for="admin_txtcomments"><?php echo __('Enter text for Comments', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Enter text for Comments.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Comments', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Comments', 'fmovie'); ?>" name="admin_txtcomments" id="admin_txtcomments"  value="<?php echo stripslashes_deep(get_option('admin_txtcomments')); ?>">
		</p>
		<!--<p>
			<label for="admin_recently"><?php echo __('Enter text for Sort by', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify Sort by option on sortby module.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Sort by', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Sort by', 'fmovie'); ?>" name="admin_recently" id="admin_recently"  value="<?php echo stripslashes_deep(get_option('admin_recently')); ?>">
		</p>-->
		<p>
			<label for="admin_mostrated"><?php echo __('Enter text for Rating', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify Rating.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Rating', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Rating', 'fmovie'); ?>" name="admin_mostrated" id="admin_mostrated"  value="<?php echo stripslashes_deep(get_option('admin_mostrated')); ?>">
		</p>
		<p>
			<label for="admin_mostwatched"><?php echo __('Enter text for Views', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify Views.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Views', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Views', 'fmovie'); ?>" name="admin_mostwatched" id="admin_mostwatched"  value="<?php echo stripslashes_deep(get_option('admin_mostwatched')); ?>">
		</p>
		<p>
			<label for="admin_language"><?php echo __('Enter text for Language', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify Language.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Language', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Language', 'fmovie'); ?>" name="admin_language" id="admin_language"  value="<?php echo stripslashes_deep(get_option('admin_language')); ?>">
		</p>
		<p>
			<label for="admin_releasedate"><?php echo __('Enter text for Date', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify Date option on sortby module.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Date', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Date', 'fmovie'); ?>" name="admin_releasedate" id="admin_releasedate"  value="<?php echo stripslashes_deep(get_option('admin_releasedate')); ?>">
		</p>
		<p>
			<label for="admin_titleato"><?php echo __('Enter text for Title', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify Title on sortby module.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Title', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Title', 'fmovie'); ?>" name="admin_titleato" id="admin_titleato"  value="<?php echo stripslashes_deep(get_option('admin_titleato')); ?>">
		</p>
		<!--<p>
			<label for="admin_fullbio"><?php echo __('Enter text for Full Bio in person page.', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify Full Bio in person page.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Full Bio', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Full Bio', 'fmovie'); ?>" name="admin_fullbio" id="admin_fullbio"  value="<?php echo stripslashes_deep(get_option('admin_fullbio')); ?>">
		</p>-->
		<!--<p>
			<label for="admin_nobio"><?php echo __('Enter text for "No bio available for" in person page.', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify Full Bio in person page.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('No bio available for', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('No bio available for', 'fmovie'); ?>" name="admin_nobio" id="admin_nobio"  value="<?php echo stripslashes_deep(get_option('admin_nobio')); ?>">
		</p>-->
		<!--<p>
			<label for="admin_testolike"><?php echo __('Enter text for Like', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify text for Like button.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Like', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Like', 'fmovie'); ?>" name="admin_testolike" id="admin_testolike"  value="<?php echo stripslashes_deep(get_option('admin_testolike')); ?>">
		</p>-->
		<p>
			<label for="admin_textfavorites"><?php echo __('Enter text for Favorites', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify text for Favorites.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Favorites', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Favorites', 'fmovie'); ?>" name="admin_textfavorites" id="admin_textfavorites"  value="<?php echo stripslashes_deep(get_option('admin_textfavorites')); ?>">
		</p>
		<p>
			<label for="admin_related"><?php echo __('Enter text for related post', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify text for Related.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('You may also like', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('You may also like', 'fmovie'); ?>" name="admin_related" id="admin_related"  value="<?php echo stripslashes_deep(get_option('admin_related')); ?>">
		</p>
		<!--<p>
			<label for="admin_txtnoletter"><?php echo __('Enter text for no letter', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify text for nothing matched letter.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('Sorry, but nothing matched this letter.', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('Sorry, but nothing matched this letter.', 'fmovie'); ?>" name="admin_txtnoletter" id="admin_txtnoletter"  value="<?php echo stripslashes_deep(get_option('admin_txtnoletter')); ?>">
		</p>-->
		<p>
			<label for="admin_textautoembed"><?php echo __('Enter text for servers note', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify servers note', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('If current server doesn\'t work please try other servers below.', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('If current server doesn\'t work please try other servers below.', 'fmovie'); ?>" name="admin_textautoembed" id="admin_textautoembed"  value="<?php echo stripslashes_deep(get_option('admin_textautoembed')); ?>">
		</p>
		<p>
			<label for="admin_btn_more"><?php echo __('Enter text for read more button on home description', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify text more small button.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('more', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('more', 'fmovie'); ?>" name="admin_btn_more" id="admin_btn_more"  value="<?php echo stripslashes_deep(get_option('admin_btn_more')); ?>">
		</p>
		<p>
			<label for="admin_btn_less"><?php echo __('Enter text for less button on home description', 'fmovie'); ?></label>
			<span class="helptext"><?php echo __('Text that identify text less small button.', 'fmovie'); ?><br><?php echo __('Default:', 'fmovie'); ?> <?php echo __('less', 'fmovie'); ?></span>
			<input type="text" placeholder="<?php echo __('less', 'fmovie'); ?>" name="admin_btn_less" id="admin_btn_less"  value="<?php echo stripslashes_deep(get_option('admin_btn_less')); ?>">
		</p>
		<p><input type="submit" name="update_options" value="<?php echo __('Save settings', 'fmovie'); ?>" class="admin-button admin-button-color-1"></p>
		</form>
	</div>
</div>

<?php get_template_part('admin/footer'); ?>