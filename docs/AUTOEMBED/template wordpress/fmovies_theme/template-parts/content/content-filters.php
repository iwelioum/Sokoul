<?php
/**
 * Template part for displaying filters
 *
 * @link https://developer.wordpress.org/themes/basics/template-hierarchy/
 *
 * @package fmovie
 */

?>

<div class="filters normal">
    <div class="filter dropdown">
        <button class="dropdown-toggle" data-toggle="dropdown" data-display="static"><i class="fas fa-folder-open"></i>
            <?php 
                if (is_category()){
                    single_cat_title();
                    } else {  
                    echo genre;
                }
            ?>
        </button>
        <ul class="dropdown-menu lg c4">
            <?php DropdownCat(); ?>
        </ul>
    </div>
    <div class="filter dropdown">
        <button class="dropdown-toggle" data-toggle="dropdown" data-display="static"><i class="fas fa-calendar-alt"></i>
            <?php 
                if (is_tax( 'years' )){
                    the_archive_title();
                    } else {  
                    echo year;
                }
            ?>
        </button>
        <ul class="dropdown-menu md c4">
            <?php echo DropdownYears(); ?>
            
        </ul>
    </div>
    <div class="filter dropdown">
        <button class="dropdown-toggle" data-toggle="dropdown" data-display="static"><i class="fas fa-globe-americas"></i>
            <?php 
                if (is_tax( 'country' )){
                    the_archive_title();
                    } else {  
                    echo country;
                }
            ?>
        </button>
        <ul class="dropdown-menu lg c4">
            <?php echo DropdownCountry(); ?>
        </ul>
    </div>
    <div class="filter dropdown">
        <button class="dropdown-toggle" data-toggle="dropdown" data-display="static"><i class="fas fa-flag"></i>
            <?php 
                if (is_tax( 'language' )){
                    the_archive_title();
                    } else {  
                    echo language;
                }
            ?>
        </button>
        <ul class="dropdown-menu c1">
            <?php echo DropdownLanguage(); ?>
        </ul>
    </div>
    <div class="filter dropdown">
        <button class="dropdown-toggle" data-toggle="dropdown" data-display="static"><i class="fas fa-cube"></i>
            <?php 
                if (is_tax( 'quality' )){
                    the_archive_title();
                    } else {  
                    echo txtquality;
                }
            ?>
        </button>
        <ul class="dropdown-menu c1">
            <?php DropdownQuality(); ?>
        </ul>
    </div>
    <div class="filter dropdown">
        <button class="dropdown-toggle" data-toggle="dropdown" data-display="static"><i class="fas fa-clone"></i>
            <?php 
                if (is_category('tv-series')){
                    echo tvseries;
                    } else if (is_category('movies')){
                     echo txtmovies;
                    } else {
                    echo esc_html__( 'Type', 'fmovie' );
                }
            ?>
        </button>
        <ul class="dropdown-menu c1">
            <?php DropdownType(); ?>
        </ul>
    </div>
    <div class="filter dropdown">
        <button class="btn btn-sm btn-primary" data-toggle="dropdown" data-display="static"><i class="fas fa-filter"></i>
            <?php 
                if(!isset($_GET['order'])) { $_GET['order'] = 'Latest'; } 
                $sortTitle = $_GET['order'];
                echo $sortTitle;
            ?>
        </button>
        <ul class="dropdown-menu sort c1">
            <li><a class="dropdown-item<?php if (isset($_GET['order'])) { if ($_GET['order'] == 'Latest') echo ' active'; } else { echo ""; } ?>" href="?order=Latest" data-label="Latest">
                <?php echo textlatest ?>
            </a> </li>
            <li><a class="dropdown-item<?php if (isset($_GET['order'])) { if ($_GET['order'] == 'Views') echo ' active'; } else { echo ""; } ?>" href="?order=Views" data-label="Views">
                <?php echo mostwatched ?>
            </a></li>
            <li><a class="dropdown-item<?php if (isset($_GET['order'])) { if ($_GET['order'] == 'Rating') echo ' active'; } else { echo ""; } ?>" href="?order=Rating" data-label="Rating">
                <?php echo mostrated ?>
            </a></li>
            <li> <a class="dropdown-item<?php if (isset($_GET['order'])) { if ($_GET['order'] == 'Year') echo ' active'; } else { echo ""; } ?>" href="?order=Year" data-label="Year">
                <?php echo year ?>
            </a></li>
            <li><a class="dropdown-item<?php if (isset($_GET['order'])) { if ($_GET['order'] == 'Title') echo ' active'; } else { echo ""; } ?>" href="?order=Title" data-label="Title">
                <?php echo titleato ?>
            </a></li>
        </ul>
    </div>
</div>


