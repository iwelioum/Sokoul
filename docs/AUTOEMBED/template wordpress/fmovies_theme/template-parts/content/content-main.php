<?php
/**
 * Template part for displaying posts in main page
 *
 * @link https://developer.wordpress.org/themes/basics/template-hierarchy/
 *
 * @package fmovie
 */

// description
$description  = get_bloginfo( 'description', 'display' );
// slider
get_template_part( 'template-parts/content/content', 'swiper' );
// cat movies
$category_movies = get_cat_ID( 'Movies' ); 
$category_link_movies = get_category_link( $category_movies );
// cat series
$category_id = get_cat_ID( 'TV Series' ); 
$category_link = get_category_link( $category_id );
// reccomended
$fmovie_reccomended = get_option('admin_reccomended');
?>

<div class="container">
	<div class="mb-5">
		<h2 class="home-title"><?php bloginfo( 'name' ); ?></h2>
		<div class="shorting slide-read-more"><?php echo deschome; // phpcs:ignore WordPress.Security.EscapeOutput ?></div>
		<div class="slide-read-more-button read-more-button"><i class="fa fa-plus"></i> <?php echo btn_more; ?></div>
		<div class="slide-read-more-button"><i class="fa fa-minus"></i> <?php echo btn_less; ?></div>
	</div>
	<?php if ($fmovie_reccomended == 1) {  ?>
		<section class="bl">
			<div class="heading">
				<h2><?php echo recommended; ?></h2>
				<div class="tabs"> 
					<span href="#!" data-slug="recommended" data-exclude="-1" class="tab active"><i class="fa fa-play-circle"></i> <?php echo txtmovies ?></span> 
					<span href="#!" data-slug="recommended" data-exclude="-2" class="tab"><i class="fa fa-list"></i> <?php echo tvseries ?></span> 
					<span href="#!" data-meta="popularity" data-orderby="meta_value_num" class="tab"><i class="fa fa-chart-line"></i> <?php echo trending; ?></span>
				</div>
				<div class="clearfix"></div>
			</div>
			<div class="content tab-content">
				<div class="filmlist no movies active">
					
					<?php 
						
						$args = array(
						'post_type' => 'post',
						'post_status' => 'publish',
						'category_name' => 'Recommended',
						'cat'=> -1,
						'showposts' => get_option('posts_per_page'),
						'no_found_rows' => true
						);
						
						$query = new WP_Query( $args );
						if ( $query->have_posts() ):
						while ( $query->have_posts() ):
						$query->the_post();
					?>
					<?php get_template_part( 'template-parts/content/content', 'loop' ); ?>
					<?php endwhile; wp_reset_postdata(); ?>
					<?php endif; ?>									
					<div class="clearfix"></div>
				</div>
			</div>
		</section>
	<?php } ?>
	<?php get_template_part( 'template-parts/content/content', 'latest' ); ?>
</div>


