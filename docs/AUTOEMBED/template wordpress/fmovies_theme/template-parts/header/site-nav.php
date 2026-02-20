<?php
/**
 * Displays the site menu & navigation.
 *
 * @package fmovie
 */

// cat movies
$category_movies = get_cat_ID( 'Movies' ); 
$category_link_movies = get_category_link( $category_movies );
// cat series
$category_id = get_cat_ID( 'TV Series' ); 
$category_link = get_category_link( $category_id );
// genre_link
$fmovie_genre_link = get_option('admin_genre_link');
// country_link
$fmovie_country_link = get_option('admin_country_link');
// top_imdb
$fmovie_top_imdb = get_option('admin_top_imdb');
// favorites_link
$fmovie_favorites_link = get_option('admin_favorites_link');
// Recommended
$slug = 'Recommended';
$cat = get_category_by_slug($slug); 
$catID = $cat->term_id;
?>

<ul id="menu">
	<li><a href="<?php echo esc_url( home_url( '/' ) ); ?>" rel="home"><?php echo esc_html__( 'Home', 'fmovie' ); ?></a></li>
	<?php if ($fmovie_genre_link == 1) {  ?>
		<li><a><?php echo genre; ?><i class="fa fa-plus clicky"></i></a>
			<ul class="genre">
				<?php wp_list_categories('title_li=&hide_empty=1&exclude=1,2,3,'.$catID.''); ?>
			</ul>
		</li>
	<?php } ?>
	<?php if ($fmovie_country_link == 1) {  ?>
		<li><a><?php echo country; ?><i class="fa fa-plus clicky2"></i></a>
			<ul class="country">
				<?php echo NavCountry(); ?>
			</ul>
		</li>
	<?php } ?>
	<li><a href="<?php echo $category_link_movies; ?>"><?php echo txtmovies; ?></a></li>
	<li><a href="<?php echo $category_link; ?>"><?php echo tvseries; ?></a></li>
	<?php if ($fmovie_top_imdb == 1) {  ?>
		<li><a href="<?php echo esc_url( home_url( '/top-imdb' ) ); ?>"><?php echo top; ?></a></li>
	<?php } ?>
	<?php if ($fmovie_favorites_link == 1) {  ?>
		<li><a href="<?php echo esc_url( home_url( '/favorites' ) ); ?>"><?php echo textfavorites; ?></a></li>
	<?php } ?>
</ul>
