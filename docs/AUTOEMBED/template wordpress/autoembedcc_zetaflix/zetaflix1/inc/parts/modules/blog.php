<?php
/*
* -------------------------------------------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @aopyright: (c) 2023 Zetathemes. All rights reserved
* -------------------------------------------------------------------------------------
*
* @since 1.0.0
*
*/

// Compose DATA
$title = zeta_get_option('blogtitle','Last entries');
$items = zeta_get_option('blogitems','5');
$words = zeta_get_option('blogwords','190');
$perml = zeta_compose_pagelink('pageblog');

// Compose Query
$query = array(
	'post_type' => array('post'),
	'showposts' => $items,
	'order' => 'desc'
);

// End Data
?>
<header>
	<h2><?php echo $title; ?></h2>
	<?php if($perml) { ?><span><a href="<?php echo $perml; ?>"><?php _z('See all'); ?></a></span><?php } ?>
</header>
<div class="list-items-blogs">
	<?php query_posts($query); ?>
	<?php if (have_posts()): while(have_posts()): the_post(); ?>
	<div class="post-entry" id="entry-<?php the_id(); ?>">
		<div class="home-blog-post">
			<div class="entry-date">
				<div class="date"><?php zeta_post_date('j'); ?></div>
				<div class="month"><?php zeta_post_date('F'); ?></div>
			</div>
			<div class="entry-datails">
				<div class="entry-title"><a href="<?php the_permalink(); ?>"><?php the_title(); ?></a></div>
				<div class="entry-content"><?php zt_content_alt($words); ?></div>
			</div>
		</div>
	</div>
	<?php endwhile;  else: ?>
	<div class="zt-no-post"><?php _z('No posts to show'); ?></div>
	<?php endif;  wp_reset_query(); ?>
</div>
