<?php
// latest
$fmovie_latest_movies = get_option('admin_latest_movies');
$fmovie_latest_series = get_option('admin_latest_series');
	
// cat movies
$category_movies = get_cat_ID( 'Movies' ); 
$category_link_movies = get_category_link( $category_movies );
// cat series
$category_id = get_cat_ID( 'TV Series' ); 
$category_link = get_category_link( $category_id );
?>
<?php if ($fmovie_latest_movies == 1) {  ?>
<section class="bl">
	<div class="heading">
		<h2><?php echo textlatest ?> <?php echo txtmovies ?></h2>
		<a class="more" href="<?php echo $category_link_movies; ?>"><?php echo textviewall ?>&nbsp;<i class="fa fa-chevron-circle-right"></i></a>
		<div class="clearfix"></div>
	</div>
	<div class="content">
		<div class="filmlist movies active">
			
			<?php 
				
				$args = array(
				'post_type' => 'post',
				'post_status' => 'publish',
				'category_name' => 'Movies',
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
<?php if ($fmovie_latest_series == 1) {  ?>
<section class="bl">
	<div class="heading">
		<h2><?php echo textlatest ?> <?php echo tvseries ?></h2>
		<a class="more" href="<?php echo $category_link; ?>"><?php echo textviewall ?>&nbsp;<i class="fa fa-chevron-circle-right"></i></a>
		<div class="clearfix"></div>
	</div>
	<div class="content">
		<div class="filmlist movies active">
			<?php 
				
				$args = array(
				'post_type' => 'post',
				'post_status' => 'publish',
				'category_name' => 'TV Series',
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