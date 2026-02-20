<?php
/**
 * Template part for displaying related posts
 *
 * @link https://developer.wordpress.org/themes/basics/template-hierarchy/
 *
 * @package fmovie
 */

$fmovie_related_post = get_option('admin_related_post');
 
?>
<?php if ($fmovie_related_post == 1) {  ?>
<?php 
	
if ( is_post_template( 'tv.php' ) ) {
$exclude = -2;
} else { 
$exclude = -1;
} 
$related_query = new WP_Query(array(
 'post_type' => 'post',
 'cat'=> $exclude,
 'category__in' => wp_get_post_categories(get_the_ID()),
 'post__not_in' => array(get_the_ID()),
 'showposts' => 12,
 'post_status' => 'publish',
 'orderby' => 'rand',
 'no_found_rows' => true
));
if ($related_query->have_posts()) { 
?>
<div class="bl-2">
	<section class="bl">
		<div class="heading simple">
			<h2 class="title"><?php echo related; ?></h2>
		</div><!-- #heading -->
		<div class="content">
			<div class="filmlist active related">
				<?php while ($related_query->have_posts()) {
					$related_query->the_post();
					get_template_part( 'template-parts/content/content', 'loop' );
				} ?>
				<div class="clearfix"></div>
			</div>
		</div><!-- #content -->
	</section><!-- #section -->
</div>
<?php 
	wp_reset_postdata(); 
}
?>
<?php } else { ?>
<style>.watch-extra .bl-1 {width: 100%;}</style>
<?php } ?>

