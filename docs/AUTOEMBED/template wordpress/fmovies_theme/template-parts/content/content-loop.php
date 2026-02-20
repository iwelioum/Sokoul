<?php
/**
 * Template part for displaying posts
 *
 * @link https://developer.wordpress.org/themes/basics/template-hierarchy/
 *
 * @package fmovie
 */

?>
<div id="post-<?php the_ID(); ?>" <?php post_class( 'item' ); ?> data-tooltip-content="#tooltipster-<?php the_ID(); ?>">
	<div class="icons">
		<?php Qua(); ?>
	</div>
	<a href="<?php the_permalink(); ?>" class="poster">
		<img class="lazyload" loading="lazy" src="<?php echo placeholder; ?>" data-src="<?php echo SinglePoster(); ?>" alt="<?php the_title(); ?>" />
	</a>
	<span class="imdb"><i class="fa fa-star"></i> <?php Average(); ?></span>
	<?php the_title( '<h3 class="entry-title"><a class="title" href="' . esc_url( get_permalink() ) . '" rel="bookmark">', '</a></h3>' ); ?>
	<?php if ( in_category('TV Series') ) { ?>
	<div class="meta"> 
	<?php number_of_seasons(); ?> <i class="dot"></i> <?php last_episode_to_air() ?> <i class="type"><?php Tipo() ?></i> 
	</div>
	<?php } else { ?>
	<div class="meta"> 
	<?php Years(); ?> <i class="dot"></i> <?php Dura(); ?> <i class="type"><?php Tipo() ?></i> 
	</div>
	<?php } ?>
	<div class="tooltip_templates">
		<div id="tooltipster-<?php the_ID(); ?>">
			<div class="title text-truncate" style="max-width: 240px;"><?php the_title(); ?></div>
			<div class="meta">
				<span class="imdb"><i class="fa fa-star"></i> <?php Average(); ?></span>
				<span><?php Years(); ?></span>
				<span><?php Dura(); ?></span>
				<span class="text-right">
					<?php Qualita(); ?>
				</span>
			</div>
			<div class="desc"><?php fmovie_excerpt('190'); ?></div>
			<div class="meta">
                <?php SingleCountry(); ?>
				<div>
					<span><?php echo genre; ?>:</span>
					<span>
						<?php tooltipGenreList(); ?>
					</span>
				</div>
			</div>
			<div class="actions">
				<a href="#" class="bookmark inactive" data-bookmark="Favorite" id="<?php the_ID(); ?>"><i class="fa fa-heart" style="font-weight: 400"></i></a>
				<a class="watchnow" href="<?php the_permalink(); ?>"><i class="fa fa-play"></i> <?php echo watch; ?></a>
				<div class="clearfix"></div>
			</div>
		</div>
	</div>
</div><!-- #post-<?php the_ID(); ?> -->