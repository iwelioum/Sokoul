<?php
/**
 * Template part for displaying swiper items
 *
 * @link https://developer.wordpress.org/themes/basics/template-hierarchy/
 *
 * @package fmovie
 */

?>
<div <?php post_class( 'item swiper-slide lazyload' ); ?> loading="lazy" style="background-image: url(<?php echo placeholder; ?>)" data-src="<?php echo Backdrop(); ?>">
    <div class="container">
        <div class="info">
            <?php the_title( '<h3 class="title">', '</h3>' ); ?>
            <div class="meta"> 
                <?php Qualita(); ?>
                <span class="imdb"> <i class="fa fa-star"></i> <?php echo Average(); ?></span> 
                <?php Durata(); ?>
                <span> 
                    <?php SingleGenre(); ?>
                </span> 
            </div>
            <div class="desc"><?php echo get_the_excerpt(); ?></div>
            <div class="actions"> 
                <a class="watchnow" href="<?php the_permalink(); ?>"><i class="fa fa-play"></i> <?php echo watch; ?></a> 
                <?php FavoriteItem(); ?>
            </div>
        </div>
    </div>
</div>